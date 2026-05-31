package com.prassistant.pr.orchestrator;

import com.prassistant.pr.aggregation.GlobalAggregator;
import com.prassistant.pr.aggregation.model.GlobalReviewReport;
import com.prassistant.pr.aggregation.model.PrMetadata;
import com.prassistant.pr.diff.model.FileChangeType;
import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.diff.service.DiffSanitizer;
import com.prassistant.pr.orchestrator.event.ReviewEventPublisher;
import com.prassistant.pr.review.FileChunkAnalyzer;
import com.prassistant.pr.review.model.FileReviewReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewOrchestratorTest {

    @Mock
    private DiffSanitizer diffSanitizer;

    @Mock
    private FileChunkAnalyzer fileChunkAnalyzer;

    @Mock
    private GlobalAggregator globalAggregator;

    @Mock
    private ReviewEventPublisher eventPublisher;

    private ReviewOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ReviewOrchestrator(diffSanitizer, fileChunkAnalyzer,
                globalAggregator, eventPublisher, 60, 30);
    }

    private SanitizedDiff sampleDiff(String path) {
        SanitizedDiff diff = new SanitizedDiff();
        diff.setFilePath(path);
        diff.setStatus(FileChangeType.MODIFIED);
        diff.setSanitizedContent("public class Test { }");
        return diff;
    }

    private FileReviewReport sampleReport(String path) {
        return FileReviewReport.builder()
                .filePath(path)
                .status(FileChangeType.MODIFIED)
                .overallSummary("test summary for " + path)
                .riskLevel(FileReviewReport.RiskLevel.LOW)
                .analysisMethod(FileReviewReport.AnalysisMethod.WHOLE_FILE)
                .build();
    }

    private PrMetadata sampleMetadata() {
        return PrMetadata.builder()
                .prUrl("https://github.com/org/repo/pull/1")
                .title("test PR")
                .baseBranch("main")
                .headBranch("feat/test")
                .totalFiles(2)
                .totalAdditions(50)
                .totalDeletions(10)
                .build();
    }

    private GlobalReviewReport sampleReport() {
        return GlobalReviewReport.builder()
                .overallSummary("PR 整体总结")
                .globalRiskLevel(GlobalReviewReport.RiskLevel.LOW)
                .globalRiskReason("风险低")
                .build();
    }

    @Nested
    @DisplayName("review() — 全流程")
    class FullFlow {

        @Test
        @DisplayName("应完成 L1→L2→L3 全流程并返回成功报告")
        void shouldCompleteFullFlow() {
            String rawDiff = "diff --git a/Test.java b/Test.java\n@@ -1,1 +1,1 @@\n-old\n+new";
            List<SanitizedDiff> diffs = List.of(sampleDiff("Test.java"));
            List<FileReviewReport> reports = List.of(sampleReport("Test.java"));
            GlobalReviewReport finalReport = sampleReport();

            when(diffSanitizer.sanitize(rawDiff)).thenReturn(diffs);
            when(fileChunkAnalyzer.analyzeAll(anyList()))
                    .thenReturn(CompletableFuture.completedFuture(reports));
            when(globalAggregator.aggregate(anyList(), any())).thenReturn(finalReport);

            GlobalReviewReport result = orchestrator.review(rawDiff, sampleMetadata());

            assertTrue(result.isSuccess());
            assertEquals("PR 整体总结", result.getOverallSummary());
            assertNotNull(result.getTaskId());

            verify(diffSanitizer).sanitize(rawDiff);
            verify(fileChunkAnalyzer).analyzeAll(diffs);
            verify(globalAggregator).aggregate(anyList(), any());
        }

        @Test
        @DisplayName("L1 返回空列表应返回错误报告")
        void shouldReturnErrorWhenL1ReturnsEmpty() {
            when(diffSanitizer.sanitize(anyString())).thenReturn(List.of());

            GlobalReviewReport result = orchestrator.review("diff", sampleMetadata());

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
            assertTrue(result.getError().contains("未解析出任何变更文件"));
        }

        @Test
        @DisplayName("L1 返回 null 应返回错误报告")
        void shouldReturnErrorWhenL1ReturnsNull() {
            when(diffSanitizer.sanitize(anyString())).thenReturn(null);

            GlobalReviewReport result = orchestrator.review("diff", sampleMetadata());

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("L2 超时应返回错误报告（使用 1ms 超时）")
        void shouldReturnErrorOnL2Timeout() {
            // 用测试构造函数注入 1ms 超时，避免等待 60s
            orchestrator = new ReviewOrchestrator(diffSanitizer, fileChunkAnalyzer,
                    globalAggregator, eventPublisher, 1, 30);

            when(diffSanitizer.sanitize(anyString()))
                    .thenReturn(List.of(sampleDiff("SlowFile.java")));
            when(fileChunkAnalyzer.analyzeAll(anyList()))
                    .thenReturn(new CompletableFuture<>());

            GlobalReviewReport result = orchestrator.review("diff", sampleMetadata());

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
            assertTrue(result.getError().contains("超时"));
        }

        @Test
        @DisplayName("L2 返回空列表应返回错误报告")
        void shouldReturnErrorWhenL2ReturnsEmpty() {
            when(diffSanitizer.sanitize(anyString()))
                    .thenReturn(List.of(sampleDiff("Test.java")));
            when(fileChunkAnalyzer.analyzeAll(anyList()))
                    .thenReturn(CompletableFuture.completedFuture(List.of()));

            GlobalReviewReport result = orchestrator.review("diff", sampleMetadata());

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("未返回任何"));
        }

        @Test
        @DisplayName("L3 异常应返回错误报告")
        void shouldReturnErrorOnL3Exception() {
            when(diffSanitizer.sanitize(anyString()))
                    .thenReturn(List.of(sampleDiff("Test.java")));
            when(fileChunkAnalyzer.analyzeAll(anyList()))
                    .thenReturn(CompletableFuture.completedFuture(
                            List.of(sampleReport("Test.java"))));
            when(globalAggregator.aggregate(anyList(), any()))
                    .thenThrow(new RuntimeException("AI 调用失败"));

            GlobalReviewReport result = orchestrator.review("diff", sampleMetadata());

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("AI 调用失败"));
        }
    }

    @Nested
    @DisplayName("review() — 错误恢复与部分失败")
    class PartialFailure {

        @Test
        @DisplayName("部分文件分析含错误仍应完成全流程")
        void shouldContinueOnPartialFileErrors() {
            when(diffSanitizer.sanitize(anyString()))
                    .thenReturn(List.of(sampleDiff("Good.java"), sampleDiff("Bad.java")));

            FileReviewReport good = sampleReport("Good.java");
            FileReviewReport bad = sampleReport("Bad.java");
            bad.setError("分析超时");
            when(fileChunkAnalyzer.analyzeAll(anyList()))
                    .thenReturn(CompletableFuture.completedFuture(List.of(good, bad)));
            when(globalAggregator.aggregate(anyList(), any()))
                    .thenReturn(sampleReport());

            GlobalReviewReport result = orchestrator.review("diff", sampleMetadata());

            assertTrue(result.isSuccess());
            verify(globalAggregator).aggregate(anyList(), any());
        }
    }

    @Nested
    @DisplayName("超时常量")
    class TimeoutConstants {

        @Test
        @DisplayName("默认 L2 超时应为 60 秒")
        void l2TimeoutShouldBe60Seconds() {
            assertEquals(60, ReviewOrchestrator.DEFAULT_L2_TIMEOUT_SECONDS);
        }

        @Test
        @DisplayName("默认 L3 超时应为 30 秒")
        void l3TimeoutShouldBe30Seconds() {
            assertEquals(30, ReviewOrchestrator.DEFAULT_L3_TIMEOUT_SECONDS);
        }
    }
}
