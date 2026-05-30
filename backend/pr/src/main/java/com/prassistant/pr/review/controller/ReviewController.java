package com.prassistant.pr.review.controller;

import com.prassistant.pr.aggregation.model.GlobalReviewReport;
import com.prassistant.pr.aggregation.model.PrMetadata;
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
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewOrchestrator orchestrator;
    private final ReviewEventPublisher eventPublisher;
    private final Executor executor;

    /** 临时结果缓存（生产环境应替换为 Redis / 数据库） */
    private final Map<String, GlobalReviewReport> resultCache = new ConcurrentHashMap<>();

    @Autowired
    public ReviewController(ReviewOrchestrator orchestrator,
                            ReviewEventPublisher eventPublisher) {
        this(orchestrator, eventPublisher, ForkJoinPool.commonPool());
    }

    /** 测试专用 — 可注入自定义 Executor（如同步执行器） */
    ReviewController(ReviewOrchestrator orchestrator,
                     ReviewEventPublisher eventPublisher,
                     Executor executor) {
        this.orchestrator = orchestrator;
        this.eventPublisher = eventPublisher;
        this.executor = executor;
    }

    /**
     * 发起 Review 分析
     *
     * <p>返回 202 Accepted 包含 taskId，分析在后台异步执行。
     * 前端可通过 {@link #streamEvents(String)} 连接 SSE 监听进度。</p>
     */
    @PostMapping
    public ResponseEntity<ReviewCreateResponse> createReview(@RequestBody ReviewRequest request) {
        if (request.rawDiff() == null || request.rawDiff().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        PrMetadata metadata = buildMetadata(request);
        String taskId = java.util.UUID.randomUUID().toString().substring(0, 8);

        // 推送任务创建事件
        eventPublisher.publish(ReviewEvent.stageEvent(
                ReviewEventType.TASK_CREATED, taskId, "分析任务已创建"));

        // 异步执行分析
        CompletableFuture.runAsync(() -> {
            try {
                GlobalReviewReport report = orchestrator.review(request.rawDiff(), metadata);
                // 补充 taskId（orchestrator 内部已设置，确保兜底）
                if (report != null && report.getTaskId() == null) {
                    report.setTaskId(taskId);
                }
                if (report != null) {
                    resultCache.put(taskId, report);
                }

                eventPublisher.publish(ReviewEvent.stageEvent(
                        ReviewEventType.COMPLETED, taskId, "分析完成"));
            } catch (Exception e) {
                log.error("Async review failed task={}: {}", taskId, e.getMessage());
                eventPublisher.publish(ReviewEvent.stageEvent(
                        ReviewEventType.FAILED, taskId, "分析失败: " + e.getMessage()));
            } finally {
                eventPublisher.complete(taskId);
            }
        }, executor);

        String eventsUrl = "/api/v1/reviews/" + taskId + "/events";
        String resultUrl = "/api/v1/reviews/" + taskId + "/result";

        return ResponseEntity.accepted()
                .body(new ReviewCreateResponse(taskId, eventsUrl, resultUrl));
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
