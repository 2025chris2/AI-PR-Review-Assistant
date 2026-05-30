package com.prassistant.pr.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prassistant.pr.aggregation.model.PrMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * GitHubApiClient 单元测试
 *
 * <p>使用 MockRestServiceServer 模拟 GitHub API 响应，不发起真实 HTTP 请求。</p>
 */
@DisplayName("GitHubApiClient")
class GitHubApiClientTest {

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";
    private static final int PR_NUMBER = 1;
    private static final String TOKEN = "ghp_test-token-abc123";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer mockServer;
    private GitHubApiClient client;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        client = new GitHubApiClient(restTemplate, objectMapper);
    }

    // ==================== 测试辅助方法 ====================

    private void assertAllPrMetadataFields(PrMetadata metadata) {
        assertEquals("Add user login feature", metadata.getTitle());
        assertEquals("Implements OAuth2-based login", metadata.getDescription());
        assertEquals("octocat", metadata.getAuthor());
        assertEquals("main", metadata.getBaseBranch());
        assertEquals("feat/login", metadata.getHeadBranch());
        assertEquals("https://github.com/octocat/hello-world/pull/1", metadata.getPrUrl());
        assertEquals(3, metadata.getTotalFiles());
        assertEquals(120, metadata.getTotalAdditions());
        assertEquals(15, metadata.getTotalDeletions());
    }

    private String samplePrJson() {
        return """
                {
                  "title": "Add user login feature",
                  "body": "Implements OAuth2-based login",
                  "user": {"login": "octocat"},
                  "base": {"ref": "main"},
                  "head": {"ref": "feat/login"},
                  "html_url": "https://github.com/octocat/hello-world/pull/1",
                  "changed_files": 3,
                  "additions": 120,
                  "deletions": 15
                }""";
    }

    private String sampleFilesJson() {
        return """
                [
                  {
                    "filename": "src/main/java/com/example/UserService.java",
                    "status": "modified",
                    "additions": 45,
                    "deletions": 10,
                    "changes": 55,
                    "patch": "@@ -1,5 +1,8 @@\\n+public class UserService {",
                    "raw_url": "https://raw.githubusercontent.com/octocat/hello-world/main/src/main/java/com/example/UserService.java"
                  },
                  {
                    "filename": "src/main/resources/application.yml",
                    "status": "added",
                    "additions": 75,
                    "deletions": 0,
                    "changes": 75,
                    "patch": "@@ -0,0 +1,75 @@\\n+spring:\\n+  application:",
                    "raw_url": "https://raw.githubusercontent.com/octocat/hello-world/main/src/main/resources/application.yml"
                  }
                ]""";
    }

    private String sampleDiffText() {
        return "diff --git a/src/main/java/com/example/UserService.java b/src/main/java/com/example/UserService.java\n"
                + "index abc..def 100644\n"
                + "--- a/src/main/java/com/example/UserService.java\n"
                + "+++ b/src/main/java/com/example/UserService.java\n"
                + "@@ -1,5 +1,8 @@\n"
                + "+public class UserService {\n";
    }

    // ==================== fetchPrMetadata ====================

    @Nested
    @DisplayName("fetchPrMetadata() — 获取 PR 元数据")
    class FetchPrMetadata {

        @Test
        @DisplayName("成功获取 PR 元数据并正确映射所有字段")
        void shouldReturnPrMetadata() {
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andExpect(method(org.springframework.http.HttpMethod.GET))
                    .andExpect(header("Authorization", "Bearer " + TOKEN))
                    .andRespond(withSuccess(samplePrJson(), MediaType.APPLICATION_JSON));

            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER + "/files"))
                    .andExpect(method(org.springframework.http.HttpMethod.GET))
                    .andExpect(header("Authorization", "Bearer " + TOKEN))
                    .andRespond(withSuccess(sampleFilesJson(), MediaType.APPLICATION_JSON));

            PrMetadata metadata = client.fetchPrMetadata(OWNER, REPO, PR_NUMBER, TOKEN);

            assertAllPrMetadataFields(metadata);
            assertTrue(metadata.getChangedFileTypes().contains("JAVA: 1"));
            assertTrue(metadata.getChangedFileTypes().contains("YML: 1"));
            mockServer.verify();
        }

        @Test
        @DisplayName("处理缺失的可空字段（user、base、head 为 null）")
        void shouldHandleMissingNullableFields() {
            String json = """
                    {
                      "title": "Fix bug",
                      "body": null,
                      "user": null,
                      "base": null,
                      "head": null,
                      "html_url": "",
                      "changed_files": 0,
                      "additions": 0,
                      "deletions": 0
                    }""";

            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER + "/files"))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            PrMetadata metadata = client.fetchPrMetadata(OWNER, REPO, PR_NUMBER, TOKEN);

            assertEquals("Fix bug", metadata.getTitle());
            assertEquals("", metadata.getDescription());
            assertEquals("", metadata.getAuthor());
            assertEquals("", metadata.getBaseBranch());
            assertEquals("", metadata.getHeadBranch());
            assertEquals("", metadata.getPrUrl());
            assertEquals(0, metadata.getTotalFiles());
            assertTrue(metadata.getChangedFileTypes().isEmpty());
            mockServer.verify();
        }

        @Test
        @DisplayName("404 时抛出 notFound 异常")
        void shouldThrowNotFoundOn404() {
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

            GitHubApiException ex = assertThrows(GitHubApiException.class,
                    () -> client.fetchPrMetadata(OWNER, REPO, PR_NUMBER, TOKEN));
            assertEquals(404, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("401 时抛出 badCredentials 异常")
        void shouldThrowBadCredentialsOn401() {
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));

            GitHubApiException ex = assertThrows(GitHubApiException.class,
                    () -> client.fetchPrMetadata(OWNER, REPO, PR_NUMBER, TOKEN));
            assertEquals(401, ex.getStatusCode());
            assertTrue(ex.getMessage().toLowerCase().contains("token"));
        }

        @Test
        @DisplayName("403 时抛出 rateLimited 异常")
        void shouldThrowRateLimitedOn403() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-RateLimit-Remaining", "0");

            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                            .headers(headers)
                            .body("{\"message\":\"API rate limit exceeded\"}")
                            .contentType(MediaType.APPLICATION_JSON));

            GitHubApiException ex = assertThrows(GitHubApiException.class,
                    () -> client.fetchPrMetadata(OWNER, REPO, PR_NUMBER, TOKEN));
            assertEquals(403, ex.getStatusCode());
            assertTrue(ex.getMessage().toLowerCase().contains("rate limit"));
        }
    }

    // ==================== fetchPrFiles ====================

    @Nested
    @DisplayName("fetchPrFiles() — 获取文件列表")
    class FetchPrFiles {

        @Test
        @DisplayName("成功获取文件列表并正确映射所有字段")
        void shouldReturnFileList() {
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER + "/files"))
                    .andExpect(method(org.springframework.http.HttpMethod.GET))
                    .andExpect(header("Authorization", "Bearer " + TOKEN))
                    .andRespond(withSuccess(sampleFilesJson(), MediaType.APPLICATION_JSON));

            List<GitHubPrFile> files = client.fetchPrFiles(OWNER, REPO, PR_NUMBER, TOKEN);

            assertEquals(2, files.size());

            GitHubPrFile first = files.get(0);
            assertEquals("src/main/java/com/example/UserService.java", first.getFilename());
            assertEquals("modified", first.getStatus());
            assertEquals(45, first.getAdditions());
            assertEquals(10, first.getDeletions());
            assertEquals(55, first.getChanges());
            assertNotNull(first.getPatch());
            assertTrue(first.getRawUrl().startsWith("https://"));

            GitHubPrFile second = files.get(1);
            assertEquals("src/main/resources/application.yml", second.getFilename());
            assertEquals("added", second.getStatus());
            assertEquals(75, second.getAdditions());
            assertEquals(0, second.getDeletions());

            mockServer.verify();
        }

        @Test
        @DisplayName("处理空文件列表")
        void shouldHandleEmptyFileList() {
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER + "/files"))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            List<GitHubPrFile> files = client.fetchPrFiles(OWNER, REPO, PR_NUMBER, TOKEN);

            assertTrue(files.isEmpty());
            mockServer.verify();
        }

        @Test
        @DisplayName("大文件 patch 字段为 null 时正常处理")
        void shouldHandleNullPatch() {
            String json = """
                    [
                      {
                        "filename": "large-file.bin",
                        "status": "added",
                        "additions": 5000,
                        "deletions": 0,
                        "changes": 5000,
                        "patch": null,
                        "raw_url": "https://raw.githubusercontent.com/octocat/hello-world/main/large-file.bin"
                      }
                    ]""";

            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER + "/files"))
                    .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

            List<GitHubPrFile> files = client.fetchPrFiles(OWNER, REPO, PR_NUMBER, TOKEN);

            assertEquals(1, files.size());
            assertNull(files.get(0).getPatch());
            mockServer.verify();
        }
    }

    // ==================== fetchRawDiff ====================

    @Nested
    @DisplayName("fetchRawDiff() — 获取原始 diff")
    class FetchRawDiff {

        @Test
        @DisplayName("成功获取原始 diff 文本")
        void shouldReturnRawDiff() {
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andExpect(method(org.springframework.http.HttpMethod.GET))
                    .andExpect(header("Authorization", "Bearer " + TOKEN))
                    .andExpect(header("Accept", GitHubApiClient.DIFF_MEDIA_TYPE))
                    .andRespond(withSuccess(sampleDiffText(), MediaType.parseMediaType(GitHubApiClient.DIFF_MEDIA_TYPE)));

            String diff = client.fetchRawDiff(OWNER, REPO, PR_NUMBER, TOKEN);

            assertNotNull(diff);
            assertTrue(diff.contains("diff --git"));
            assertTrue(diff.contains("UserService.java"));
            mockServer.verify();
        }

        @Test
        @DisplayName("返回空 diff 时正常处理")
        void shouldHandleBlankDiff() {
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andExpect(header("Accept", GitHubApiClient.DIFF_MEDIA_TYPE))
                    .andRespond(withSuccess("", MediaType.parseMediaType(GitHubApiClient.DIFF_MEDIA_TYPE)));

            // RestTemplate.exchange() 对空响应体返回 null
            String diff = client.fetchRawDiff(OWNER, REPO, PR_NUMBER, TOKEN);

            assertNull(diff);
            mockServer.verify();
        }

        @Test
        @DisplayName("404 时抛出 notFound 异常")
        void shouldThrowNotFoundOn404() {
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

            GitHubApiException ex = assertThrows(GitHubApiException.class,
                    () -> client.fetchRawDiff(OWNER, REPO, PR_NUMBER, TOKEN));
            assertEquals(404, ex.getStatusCode());
        }

        @Test
        @DisplayName("401 时抛出 badCredentials 异常")
        void shouldThrowBadCredentialsOn401() {
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));

            GitHubApiException ex = assertThrows(GitHubApiException.class,
                    () -> client.fetchRawDiff(OWNER, REPO, PR_NUMBER, TOKEN));
            assertEquals(401, ex.getStatusCode());
        }
    }

    // ==================== fetchAll ====================

    @Nested
    @DisplayName("fetchAll() — 一站式获取")
    class FetchAll {

        @Test
        @DisplayName("组合返回 PrMetadata 和 rawDiff")
        void shouldReturnCombinedData() {
            // PR metadata
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andExpect(method(org.springframework.http.HttpMethod.GET))
                    .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                    .andRespond(withSuccess(samplePrJson(), MediaType.APPLICATION_JSON));

            // PR files (called internally by fetchPrMetadata for changedFileTypes)
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER + "/files"))
                    .andRespond(withSuccess(sampleFilesJson(), MediaType.APPLICATION_JSON));

            // Raw diff
            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andExpect(method(org.springframework.http.HttpMethod.GET))
                    .andExpect(header("Accept", GitHubApiClient.DIFF_MEDIA_TYPE))
                    .andRespond(withSuccess(sampleDiffText(),
                            MediaType.parseMediaType(GitHubApiClient.DIFF_MEDIA_TYPE)));

            GitHubPrData data = client.fetchAll(OWNER, REPO, PR_NUMBER, TOKEN);

            assertNotNull(data);
            assertAllPrMetadataFields(data.metadata());
            assertNotNull(data.rawDiff());
            assertTrue(data.rawDiff().contains("diff --git"));
            mockServer.verify();
        }
    }

    // ==================== 频率限制 ====================

    @Nested
    @DisplayName("rateLimit — 频率限制检测")
    class RateLimitTest {

        @Test
        @DisplayName("剩余量低于 5 时不抛异常（仅记录警告）")
        void shouldNotThrowWhenRateLimitLow() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-RateLimit-Remaining", "3");
            headers.add("X-RateLimit-Reset", "1234567890");

            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andRespond(withSuccess(samplePrJson(), MediaType.APPLICATION_JSON)
                            .headers(headers));

            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER + "/files"))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            // 不应抛异常
            PrMetadata metadata = client.fetchPrMetadata(OWNER, REPO, PR_NUMBER, TOKEN);
            assertNotNull(metadata);
            mockServer.verify();
        }

        @Test
        @DisplayName("剩余量充足时不影响正常调用")
        void shouldWorkWithSufficientRateLimit() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-RateLimit-Remaining", "58");

            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER))
                    .andRespond(withSuccess(samplePrJson(), MediaType.APPLICATION_JSON)
                            .headers(headers));

            mockServer.expect(requestTo("/repos/" + OWNER + "/" + REPO + "/pulls/" + PR_NUMBER + "/files"))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            PrMetadata metadata = client.fetchPrMetadata(OWNER, REPO, PR_NUMBER, TOKEN);
            assertNotNull(metadata);
            mockServer.verify();
        }
    }

    // ==================== GitHubApiException ====================

    @Nested
    @DisplayName("GitHubApiException — 异常工厂方法")
    class ExceptionTests {

        @Test
        @DisplayName("notFound 异常信息正确")
        void notFoundException() {
            GitHubApiException ex = GitHubApiException.notFound(OWNER, REPO, PR_NUMBER);
            assertEquals(404, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("octocat"));
            assertTrue(ex.getMessage().contains("hello-world"));
            assertTrue(ex.getMessage().contains("#1"));
        }

        @Test
        @DisplayName("badCredentials 异常信息正确")
        void badCredentialsException() {
            GitHubApiException ex = GitHubApiException.badCredentials();
            assertEquals(401, ex.getStatusCode());
            assertTrue(ex.getMessage().toLowerCase().contains("token"));
        }

        @Test
        @DisplayName("rateLimited 异常信息正确")
        void rateLimitedException() {
            GitHubApiException ex = GitHubApiException.rateLimited("1234567890");
            assertEquals(403, ex.getStatusCode());
            assertTrue(ex.getMessage().toLowerCase().contains("rate limit"));
        }
    }
}
