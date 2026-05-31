package com.prassistant.pr.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prassistant.pr.aggregation.model.PrMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GitHub REST API v3 客户端
 *
 * <p>封装三个核心端点，提供 PR 元数据、文件列表和原始 diff 的获取。
 * Token 按请求传入，不全局持久化。</p>
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>GET /repos/{owner}/{repo}/pulls/{number} — PR 元数据</li>
 *   <li>GET /repos/{owner}/{repo}/pulls/{number}/files — 文件列表</li>
 *   <li>GET /repos/{owner}/{repo}/pulls/{number} (Accept: diff) — 原始 diff</li>
 * </ul>
 *
 * <h3>频率限制</h3>
 * <p>每次响应后检查 X-RateLimit-Remaining，剩余 &lt; 5 时记录警告日志。
 * 超限（403/429）时抛出 {@link GitHubApiException#rateLimited}。</p>
 */
@Service
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    static final String BASE_URL = "https://api.github.com";
    static final String DIFF_MEDIA_TYPE = "application/vnd.github.v3.diff";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Spring 自动注入构造函数
     *
     * @param restTemplateBuilder Boot 预配置的 RestTemplateBuilder
     * @param objectMapper        Jackson ObjectMapper
     */
    @Autowired
    public GitHubApiClient(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder.rootUri(BASE_URL).build();
        this.objectMapper = objectMapper;
    }

    /**
     * 测试专用 — 直接注入 RestTemplate
     *
     * <p>允许测试使用 {@link org.springframework.test.web.client.MockRestServiceServer}
     * 拦截请求，不发起真实 HTTP 调用。</p>
     */
    GitHubApiClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================== 公开 API ====================

    /**
     * 一站式获取 PR 完整数据
     *
     * <p>依次调用 {@link #fetchPrMetadata} 和 {@link #fetchRawDiff}，
     * 合并为 {@link GitHubPrData} 返回。</p>
     */
    public GitHubPrData fetchAll(String owner, String repo, int prNumber, String token) {
        log.info("Fetching all PR data: {}/{}#{}", owner, repo, prNumber);
        PrMetadata metadata = fetchPrMetadata(owner, repo, prNumber, token);
        String rawDiff = fetchRawDiff(owner, repo, prNumber, token);
        return new GitHubPrData(metadata, rawDiff);
    }

    /**
     * 获取 PR 元数据
     *
     * <p>调用 GET /repos/{owner}/{repo}/pulls/{number}，将接口返回值映射为
     * {@link PrMetadata}。其中 changedFileTypes 通过额外调用文件列表端点派生。</p>
     */
    public PrMetadata fetchPrMetadata(String owner, String repo, int prNumber, String token) {
        log.debug("Fetching PR metadata: {}/{}#{}", owner, repo, prNumber);

        String url = "/repos/{owner}/{repo}/pulls/{number}";
        JsonNode root = getForJson(url, token, owner, repo, String.valueOf(prNumber));

        String title = jsonTextOrEmpty(root, "title");
        String description = jsonTextOrEmpty(root, "body");
        String author = jsonPathOrEmpty(root, "user", "login");
        String baseBranch = jsonPathOrEmpty(root, "base", "ref");
        String headBranch = jsonPathOrEmpty(root, "head", "ref");
        String prUrl = jsonTextOrEmpty(root, "html_url");
        int totalFiles = jsonIntOrZero(root, "changed_files");
        int totalAdditions = jsonIntOrZero(root, "additions");
        int totalDeletions = jsonIntOrZero(root, "deletions");

        // 从文件列表派生变更文件类型分布
        List<GitHubPrFile> files = fetchPrFiles(owner, repo, prNumber, token);
        List<String> changedFileTypes = deriveFileTypes(files);

        return PrMetadata.builder()
                .title(title)
                .description(description)
                .author(author)
                .baseBranch(baseBranch)
                .headBranch(headBranch)
                .prUrl(prUrl)
                .totalFiles(totalFiles)
                .totalAdditions(totalAdditions)
                .totalDeletions(totalDeletions)
                .changedFileTypes(changedFileTypes)
                .build();
    }

    /**
     * 获取 PR 变更文件列表
     *
     * <p>调用 GET /repos/{owner}/{repo}/pulls/{number}/files。
     * 注意：GitHub 默认单页最多 30 条，大型 PR 需翻页处理（当前暂不实现翻页）。</p>
     *
     * <p>返回的 {@link GitHubPrFile#getPatch()} 可直接传给
     * {@code DiffSanitizer.sanitizeSingleFile()}。</p>
     */
    public List<GitHubPrFile> fetchPrFiles(String owner, String repo, int prNumber, String token) {
        log.debug("Fetching PR files: {}/{}#{}", owner, repo, prNumber);

        String url = "/repos/{owner}/{repo}/pulls/{number}/files";
        JsonNode root = getForJson(url, token, owner, repo, String.valueOf(prNumber));

        List<GitHubPrFile> files = new ArrayList<>();
        if (root != null && root.isArray()) {
            for (JsonNode node : root) {
                files.add(GitHubPrFile.builder()
                        .filename(jsonTextOrEmpty(node, "filename"))
                        .status(jsonTextOrEmpty(node, "status"))
                        .additions(jsonIntOrZero(node, "additions"))
                        .deletions(jsonIntOrZero(node, "deletions"))
                        .changes(jsonIntOrZero(node, "changes"))
                        .patch(jsonTextOrNull(node, "patch"))
                        .rawUrl(jsonTextOrEmpty(node, "raw_url"))
                        .build());
            }
        }

        log.debug("Fetched {} files from {}/{}#{}", files.size(), owner, repo, prNumber);
        return files;
    }

    /**
     * 获取原始 diff 文本
     *
     * <p>调用 GET /repos/{owner}/{repo}/pulls/{number}，
     * 设置 {@code Accept: application/vnd.github.v3.diff} 头，
     * 返回 multi-file unified diff 字符串。</p>
     *
     * <p>返回值可直接传给 {@code DiffSanitizer.sanitize()}。</p>
     */
    public String fetchRawDiff(String owner, String repo, int prNumber, String token) {
        log.debug("Fetching raw diff: {}/{}#{}", owner, repo, prNumber);

        String url = "/repos/{owner}/{repo}/pulls/{number}";

        HttpHeaders headers = new HttpHeaders();
        setAuthIfPresent(headers, token);
        headers.setAccept(List.of(MediaType.parseMediaType(DIFF_MEDIA_TYPE)));

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class,
                    owner, repo, String.valueOf(prNumber));
        } catch (HttpClientErrorException e) {
            throw mapError(e, owner, repo, prNumber);
        } catch (RestClientException e) {
            throw new GitHubApiException("Failed to fetch raw diff: " + e.getMessage(), e);
        }

        checkRateLimit(response.getHeaders());
        return response.getBody();
    }

    // ==================== 内部方法 ====================

    /**
     * 通用的 JSON GET 请求
     *
     * <p>当 token 不为空时设置 Bearer Token 认证头，否则以匿名方式请求。
     * 公开仓库无需 token。</p>
     */
    private JsonNode getForJson(String urlTemplate, String token, String... uriVars) {
        HttpHeaders headers = new HttpHeaders();
        setAuthIfPresent(headers, token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    urlTemplate, HttpMethod.GET, new HttpEntity<>(headers), String.class, (Object[]) uriVars);
        } catch (HttpClientErrorException e) {
            throw mapError(e, uriVars[0], uriVars[1], Integer.parseInt(uriVars[2]));
        } catch (RestClientException e) {
            throw new GitHubApiException("GitHub API request failed: " + e.getMessage(), e);
        }

        checkRateLimit(response.getHeaders());

        String body = response.getBody();
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new GitHubApiException("Failed to parse GitHub API response: " + e.getMessage(), e);
        }
    }

    /** 当 token 不为空时设置 Bearer 认证头 */
    private static void setAuthIfPresent(HttpHeaders headers, String token) {
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
    }

    /** 将 HttpClientErrorException 映射为有意义的 GitHubApiException */
    private GitHubApiException mapError(HttpClientErrorException e,
                                         String owner, String repo, int prNumber) {
        int status = e.getStatusCode().value();
        switch (status) {
            case 401:
                return GitHubApiException.badCredentials();
            case 404:
                return GitHubApiException.notFound(owner, repo, prNumber);
            case 403:
            case 429:
                String reset = e.getResponseHeaders() != null
                        ? e.getResponseHeaders().getFirst("X-RateLimit-Reset") : "unknown";
                return GitHubApiException.rateLimited(reset);
            default:
                return new GitHubApiException(status,
                        "GitHub API error (HTTP " + status + "): " + e.getMessage());
        }
    }

    /** 检查频率限制响应头，接近限制时记录警告 */
    private void checkRateLimit(HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        if (remaining != null) {
            try {
                int remainingInt = Integer.parseInt(remaining);
                if (remainingInt < 5) {
                    String reset = headers.getFirst("X-RateLimit-Reset");
                    log.warn("GitHub API rate limit low: {} remaining, resets at {}",
                            remainingInt, reset);
                }
            } catch (NumberFormatException ignored) {
                // header 格式异常不阻塞主流程
            }
        }
    }

    /** 从文件列表中派生文件类型分布（如 ["JAVA: 8", "XML: 2"]） */
    private List<String> deriveFileTypes(List<GitHubPrFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        Map<String, Long> typeCounts = files.stream()
                .map(GitHubPrFile::getFilename)
                .filter(name -> name != null && name.contains("."))
                .map(name -> {
                    int dot = name.lastIndexOf('.');
                    return name.substring(dot + 1).toUpperCase();
                })
                .filter(ext -> !ext.isEmpty())
                .collect(Collectors.groupingBy(ext -> ext, LinkedHashMap::new, Collectors.counting()));

        return typeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.toList());
    }

    // ==================== JSON 辅助方法 ====================

    private String jsonTextOrEmpty(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return "";
        return node.get(field).asText();
    }

    private String jsonPathOrEmpty(JsonNode node, String parentField, String childField) {
        if (node == null || !node.has(parentField) || node.get(parentField).isNull()) return "";
        JsonNode parent = node.get(parentField);
        if (!parent.has(childField) || parent.get(childField).isNull()) return "";
        return parent.get(childField).asText();
    }

    private String jsonTextOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    private int jsonIntOrZero(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return 0;
        return node.get(field).asInt();
    }
}
