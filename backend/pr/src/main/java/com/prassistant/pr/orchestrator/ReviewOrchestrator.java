package com.prassistant.pr.orchestrator;

import com.prassistant.pr.aggregation.GlobalAggregator;
import com.prassistant.pr.aggregation.model.GlobalReviewReport;
import com.prassistant.pr.aggregation.model.PrMetadata;
import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.diff.service.DiffSanitizer;
import com.prassistant.pr.orchestrator.model.ReviewState;
import com.prassistant.pr.orchestrator.model.ReviewTask;
import com.prassistant.pr.review.FileChunkAnalyzer;
import com.prassistant.pr.review.model.FileReviewReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 编排调度层 — 驱动 L1→L2→L3 全流程
 *
 * <p>单入口 {@link #review(String, PrMetadata)} 接收原始 Diff 和 PR 元数据，
 * 串行驱动三层分析管线，管理任务生命周期（状态机），
 * 收集各层耗时和错误，最终返回 {@link GlobalReviewReport}。</p>
 *
 * <p>异常处理策略：</p>
 * <ul>
 *   <li><b>非致命错误</b>（单个文件分析失败）— 继续执行，记录错误到 task.errors</li>
 *   <li><b>致命错误</b>（层异常 / 超时）— 中断流程，返回 FAILED 报告</li>
 * </ul>
 */
@Service
public class ReviewOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReviewOrchestrator.class);

    /** L2 整批分析超时（秒） */
    static final long DEFAULT_L2_TIMEOUT_SECONDS = 60;

    /** L3 全局聚合超时（秒） */
    static final long DEFAULT_L3_TIMEOUT_SECONDS = 30;

    private final DiffSanitizer diffSanitizer;
    private final FileChunkAnalyzer fileChunkAnalyzer;
    private final GlobalAggregator globalAggregator;
    private final long l2TimeoutSeconds;
    private final long l3TimeoutSeconds;

    @Autowired
    public ReviewOrchestrator(DiffSanitizer diffSanitizer,
                              FileChunkAnalyzer fileChunkAnalyzer,
                              GlobalAggregator globalAggregator) {
        this(diffSanitizer, fileChunkAnalyzer, globalAggregator,
                DEFAULT_L2_TIMEOUT_SECONDS, DEFAULT_L3_TIMEOUT_SECONDS);
    }

    /** 测试专用 — 可注入自定义超时 */
    ReviewOrchestrator(DiffSanitizer diffSanitizer,
                       FileChunkAnalyzer fileChunkAnalyzer,
                       GlobalAggregator globalAggregator,
                       long l2TimeoutSeconds,
                       long l3TimeoutSeconds) {
        this.diffSanitizer = diffSanitizer;
        this.fileChunkAnalyzer = fileChunkAnalyzer;
        this.globalAggregator = globalAggregator;
        this.l2TimeoutSeconds = l2TimeoutSeconds;
        this.l3TimeoutSeconds = l3TimeoutSeconds;
    }

    /**
     * 全流程编排入口
     *
     * @param rawDiff 原始 Git Diff 文本
     * @param metadata PR 元数据
     * @return 全局 Review 报告（成功或失败均返回）
     */
    public GlobalReviewReport review(String rawDiff, PrMetadata metadata) {
        ReviewTask task = createTask(metadata);
        log.info("Orchestration start task={}, pr={}", task.getTaskId(), metadata.getPrUrl());

        try {
            // ===== L1: Diff 去噪与 Hunk 切分 =====
            List<SanitizedDiff> sanitizedDiffs = runL1(rawDiff, task);

            // ===== L2: 逐文件并发分析 =====
            List<FileReviewReport> fileReports = runL2(sanitizedDiffs, task);

            // ===== L3: 全局聚合分析 =====
            GlobalReviewReport report = runL3(fileReports, metadata, task);
            report.setTaskId(task.getTaskId());

            task.transitionTo(ReviewState.COMPLETED);
            log.info("Orchestration complete task={}, risk={}, time={}ms",
                    task.getTaskId(), report.getGlobalRiskLevel(), report.getAnalysisTimeMs());

            return report;

        } catch (Exception e) {
            log.error("Orchestration failed task={}: {}", task.getTaskId(), e.getMessage());
            task.fail(e.getMessage());
            return buildErrorReport(task, e.getMessage());
        }
    }

    // ====== L1 ======

    private List<SanitizedDiff> runL1(String rawDiff, ReviewTask task) {
        log.debug("L1 start task={}", task.getTaskId());
        task.transitionTo(ReviewState.L1_SANITIZING);
        Instant l1Start = Instant.now();

        List<SanitizedDiff> diffs = diffSanitizer.sanitize(rawDiff);

        long elapsed = Duration.between(l1Start, Instant.now()).toMillis();
        log.info("L1 complete task={}, files={}, time={}ms", task.getTaskId(), diffs.size(), elapsed);

        if (diffs == null || diffs.isEmpty()) {
            throw new IllegalStateException("L1: 未解析出任何变更文件");
        }
        return diffs;
    }

    // ====== L2 ======

    private List<FileReviewReport> runL2(List<SanitizedDiff> diffs, ReviewTask task) throws Exception {
        log.debug("L2 start task={}, files={}", task.getTaskId(), diffs.size());
        task.transitionTo(ReviewState.L2_ANALYZING);
        Instant l2Start = Instant.now();

        List<FileReviewReport> reports;
        try {
            reports = fileChunkAnalyzer.analyzeAll(diffs)
                    .get(l2TimeoutSeconds, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("L2: 文件分析超时（" + l2TimeoutSeconds + "s）");
        }

        long elapsed = Duration.between(l2Start, Instant.now()).toMillis();
        log.info("L2 complete task={}, files={}, time={}ms", task.getTaskId(), reports.size(), elapsed);

        if (reports == null || reports.isEmpty()) {
            throw new IllegalStateException("L2: 未返回任何文件分析结果");
        }

        // 收集非致命错误
        collectFileErrors(reports, task);
        return reports;
    }

    /** 收集各文件中标记为 error 的报告 */
    private void collectFileErrors(List<FileReviewReport> reports, ReviewTask task) {
        for (FileReviewReport report : reports) {
            if (report.getError() != null && !report.getError().isBlank()) {
                task.addError(report.getFilePath() + ": " + report.getError());
                log.warn("File analysis error task={}, file={}: {}",
                        task.getTaskId(), report.getFilePath(), report.getError());
            }
        }
    }

    // ====== L3 ======

    private GlobalReviewReport runL3(List<FileReviewReport> fileReports, PrMetadata metadata,
                                      ReviewTask task) {
        log.debug("L3 start task={}", task.getTaskId());
        task.transitionTo(ReviewState.L3_AGGREGATING);
        Instant l3Start = Instant.now();

        GlobalReviewReport report = globalAggregator.aggregate(fileReports, metadata);

        long elapsed = Duration.between(l3Start, Instant.now()).toMillis();
        log.info("L3 complete task={}, risk={}, time={}ms",
                task.getTaskId(), report.getGlobalRiskLevel(), elapsed);

        return report;
    }

    // ====== 辅助方法 ======

    private ReviewTask createTask(PrMetadata metadata) {
        return ReviewTask.builder()
                .taskId(UUID.randomUUID().toString().substring(0, 8))
                .prUrl(metadata != null ? metadata.getPrUrl() : null)
                .state(ReviewState.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    private GlobalReviewReport buildErrorReport(ReviewTask task, String errorMessage) {
        return GlobalReviewReport.builder()
                .taskId(task.getTaskId())
                .overallSummary("分析失败")
                .globalRiskLevel(GlobalReviewReport.RiskLevel.LOW)
                .globalRiskReason(errorMessage)
                .error(errorMessage)
                .build();
    }
}
