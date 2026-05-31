package com.prassistant.pr.review.controller;

import com.prassistant.pr.aggregation.model.GlobalReviewReport;
import com.prassistant.pr.aggregation.model.PrMetadata;
import com.prassistant.pr.github.GitHubApiClient;
import com.prassistant.pr.github.GitHubApiException;
import com.prassistant.pr.github.GitHubPrData;
import com.prassistant.pr.orchestrator.ReviewOrchestrator;
import com.prassistant.pr.orchestrator.event.ReviewEvent;
import com.prassistant.pr.orchestrator.event.ReviewEventPublisher;
import com.prassistant.pr.orchestrator.event.ReviewEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Review REST API — 触发分析、SSE 推送进度、查询结果
 *
 * <p>SSE 事件流由 {@link ReviewOrchestrator} 内部推送，Controller 仅发起异步任务
 * 并负责发布 {@link ReviewEventType#RESULT} 和 {@link ReviewEventType#ERROR} 终态事件。</p>
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewOrchestrator orchestrator;
    private final ReviewEventPublisher eventPublisher;
    private final GitHubApiClient gitHubApiClient;
    private final Executor executor;

    /** 临时结果缓存（生产环境应替换为 Redis / 数据库） */
    private final Map<String, GlobalReviewReport> resultCache = new ConcurrentHashMap<>();

    @Autowired
    public ReviewController(ReviewOrchestrator orchestrator,
                            ReviewEventPublisher eventPublisher,
                            GitHubApiClient gitHubApiClient) {
        this(orchestrator, eventPublisher, gitHubApiClient, ForkJoinPool.commonPool());
    }

    /** 测试专用 — 可注入自定义 Executor（如同步执行器） */
    ReviewController(ReviewOrchestrator orchestrator,
                     ReviewEventPublisher eventPublisher,
                     GitHubApiClient gitHubApiClient,
                     Executor executor) {
        this.orchestrator = orchestrator;
        this.eventPublisher = eventPublisher;
        this.gitHubApiClient = gitHubApiClient;
        this.executor = executor;
    }

    /**
     * 发起 Review 分析（手动传入 Raw Diff）
     *
     * <p>返回 202 Accepted 包含 taskId，分析在后台异步执行。
     * 分析期间 Orchestrator 通过 SSE 推送进度事件，完成后 Controller
     * 推送 RESULT 或 ERROR 事件。</p>
     */
    @PostMapping
    public ResponseEntity<ReviewCreateResponse> createReview(@RequestBody ReviewRequest request) {
        if (request.rawDiff() == null || request.rawDiff().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        PrMetadata metadata = buildMetadata(request);
        String taskId = startAsyncAnalysis(request.rawDiff(), metadata);

        return buildAcceptedResponse(taskId);
    }

    /**
     * SSE 事件流 — 监听指定任务的分析进度
     */
    @GetMapping("/{taskId}/events")
    public SseEmitter streamEvents(@PathVariable String taskId) {
        SseEmitter emitter = eventPublisher.register(taskId);
        log.debug("SSE connected task={}", taskId);
        return emitter;
    }

    /**
     * 查询分析结果
     *
     * <p>任务完成后返回 {@link GlobalReviewReport}；仍在执行返回 202。</p>
     */
    @GetMapping("/{taskId}/result")
    public ResponseEntity<?> getResult(@PathVariable String taskId) {
        GlobalReviewReport report = resultCache.get(taskId);
        if (report != null) {
            return ResponseEntity.ok(report);
        }
        return ResponseEntity.accepted()
                .body(Map.of("taskId", taskId, "status", "PENDING"));
    }

    /**
     * 异步执行分析 — 只负责起止事件，中间进度由 Orchestrator 推送
     *
     * @return taskId
     */
    private String startAsyncAnalysis(String rawDiff, PrMetadata metadata) {
        String taskId = java.util.UUID.randomUUID().toString().substring(0, 8);

        // 同步推送 task.started
        eventPublisher.publish(ReviewEvent.stageEvent(
                ReviewEventType.TASK_STARTED, taskId, "分析任务已启动",
                Map.of("taskId", taskId, "totalFiles", metadata != null ? metadata.getTotalFiles() : 0)));

        // 异步执行分析
        CompletableFuture.runAsync(() -> {
            try {
                // orchestrator 内部推送 L1/L2/L3 进度，使用外部 taskId 保证 SSE 路由一致
                GlobalReviewReport report = orchestrator.review(rawDiff, metadata, taskId);

                // 补充 taskId（确保兜底）
                if (report != null && report.getTaskId() == null) {
                    report.setTaskId(taskId);
                }
                if (report != null) {
                    resultCache.put(taskId, report);
                }

                // 推送 result 事件
                eventPublisher.publish(ReviewEvent.stageEvent(
                        ReviewEventType.RESULT, taskId, "分析完成"));

            } catch (Exception e) {
                log.error("Async review failed task={}: {}", taskId, e.getMessage());
                eventPublisher.publish(ReviewEvent.errorEvent(
                        taskId, "PIPELINE", "分析失败: " + e.getMessage(), false));
            } finally {
                eventPublisher.complete(taskId);
            }
        }, executor);

        return taskId;
    }

    /** 构建 202 Accepted 响应 */
    private ResponseEntity<ReviewCreateResponse> buildAcceptedResponse(String taskId) {
        String eventsUrl = "/api/v1/reviews/" + taskId + "/events";
        String resultUrl = "/api/v1/reviews/" + taskId + "/result";

        return ResponseEntity.accepted()
                .body(new ReviewCreateResponse(taskId, eventsUrl, resultUrl));
    }

    /**
     * 发起 Review 分析（从 GitHub 自动拉取数据）
     *
     * <p>根据 owner/repo/prNumber 从 GitHub API 获取 PR 元数据和原始 diff，
     * 然后异步执行分析。Token 按请求传入，不持久化。</p>
     */
    @PostMapping("/github")
    public ResponseEntity<?> createReviewFromGithub(@RequestBody GitHubReviewRequest request) {
        if (request.owner() == null || request.owner().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "owner is required"));
        }
        if (request.repo() == null || request.repo().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "repo is required"));
        }
        if (request.prNumber() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "prNumber must be positive"));
        }
        if (request.token() == null || request.token().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "token is required"));
        }

        try {
            GitHubPrData prData = gitHubApiClient.fetchAll(
                    request.owner(), request.repo(), request.prNumber(), request.token());

            String taskId = startAsyncAnalysis(prData.rawDiff(), prData.metadata());
            return buildAcceptedResponse(taskId);

        } catch (GitHubApiException e) {
            log.warn("GitHub API error: {} (status={})", e.getMessage(), e.getStatusCode());
            return ResponseEntity.status(mapGitHubStatus(e.getStatusCode()))
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 发起 Review 分析（通过 GitHub PR URL 自动拉取数据）
     *
     * <p>解析 GitHub PR URL，自动提取 owner/repo/prNumber，然后从 GitHub API
     * 获取 PR 元数据和原始 diff 并分析。Token 可选，公开仓库不需要。</p>
     */
    @PostMapping("/by-url")
    public ResponseEntity<?> createReviewByUrl(@RequestBody ByUrlReviewRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }

        var matcher = PR_URL_PATTERN.matcher(request.url().strip());
        if (!matcher.matches()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Invalid GitHub PR URL. Expected: https://github.com/owner/repo/pull/123"));
        }

        String owner = matcher.group(1);
        String repo = matcher.group(2);
        int prNumber = Integer.parseInt(matcher.group(3));

        try {
            GitHubPrData prData = gitHubApiClient.fetchAll(owner, repo, prNumber, request.token());

            String taskId = startAsyncAnalysis(prData.rawDiff(), prData.metadata());
            return buildAcceptedResponse(taskId);

        } catch (GitHubApiException e) {
            log.warn("GitHub API error: {} (status={})", e.getMessage(), e.getStatusCode());
            return ResponseEntity.status(mapGitHubStatus(e.getStatusCode()))
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private static final java.util.regex.Pattern PR_URL_PATTERN =
            java.util.regex.Pattern.compile(
                    "^(?:https?://)?github\\.com/([^/]+)/([^/]+)/pull/(\\d+)(?:/.*)?$");

    /** GitHub API 错误码 → HTTP 状态码映射 */
    private int mapGitHubStatus(int gitHubStatusCode) {
        return gitHubStatusCode > 0 ? gitHubStatusCode : HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private PrMetadata buildMetadata(ReviewRequest request) {
        return PrMetadata.builder()
                .prUrl(request.prUrl())
                .title(request.title())
                .description(request.description())
                .author(request.author())
                .baseBranch(request.baseBranch())
                .headBranch(request.headBranch())
                .totalFiles(request.totalFiles())
                .totalAdditions(request.totalAdditions())
                .totalDeletions(request.totalDeletions())
                .changedFileTypes(request.changedFileTypes())
                .build();
    }
}
