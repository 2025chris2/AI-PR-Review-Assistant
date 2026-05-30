package com.prassistant.pr.review.analyzer;

import com.prassistant.pr.diff.model.FileChangeType;
import com.prassistant.pr.review.model.ChunkReviewResult;
import com.prassistant.pr.review.model.FileReviewReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileReportReducerTest {

    private FileReportReducer reducer;

    @BeforeEach
    void setUp() {
        reducer = new FileReportReducer();
    }

    private ChunkReviewResult chunkResult(String chunkId, String summary,
                                           ChunkReviewResult.RiskLevel riskLevel,
                                           List<ChunkReviewResult.RiskItem> risks,
                                           String crossChunkHints) {
        return ChunkReviewResult.builder()
            .chunkId(chunkId)
            .summary(summary)
            .riskLevel(riskLevel)
            .risks(risks)
            .crossChunkHints(crossChunkHints)
            .build();
    }

    @Nested
    @DisplayName("reduce() — 基本功能")
    class Reduce {

        @Test
        @DisplayName("空结果列表应返回默认报告")
        void shouldReturnDefaultReportForEmptyResults() {
            FileReviewReport report = reducer.reduce(List.of(), "EmptyFile.java", FileChangeType.ADDED);

            assertEquals("EmptyFile.java", report.getFilePath());
            assertEquals(FileChangeType.ADDED, report.getStatus());
            assertEquals("无分析结果", report.getOverallSummary());
            assertEquals(FileReviewReport.RiskLevel.LOW, report.getRiskLevel());
            assertTrue(report.isSuccess());
        }

        @Test
        @DisplayName("null 结果列表应返回默认报告")
        void shouldReturnDefaultReportForNullResults() {
            FileReviewReport report = reducer.reduce(null, "NullFile.java", FileChangeType.MODIFIED);

            assertEquals("NullFile.java", report.getFilePath());
            assertEquals("无分析结果", report.getOverallSummary());
        }

        @Test
        @DisplayName("单个 Chunk 结果应正确映射")
        void shouldMapSingleChunkResult() {
            var risk = new ChunkReviewResult.RiskItem("NullPointer", 10, "Potential NPE");
            var suggestion = new ChunkReviewResult.SuggestionItem(1, "Add null check");
            ChunkReviewResult result = chunkResult("test#hunk-1", "Fixed NPE",
                ChunkReviewResult.RiskLevel.HIGH, List.of(risk), null);
            result.setSuggestions(List.of(suggestion));

            FileReviewReport report = reducer.reduce(List.of(result), "UserService.java", FileChangeType.MODIFIED);

            assertEquals("UserService.java", report.getFilePath());
            assertEquals("Fixed NPE", report.getOverallSummary());
            assertEquals(FileReviewReport.RiskLevel.HIGH, report.getRiskLevel());
            assertEquals(1, report.getRisks().size());
            assertEquals("NullPointer", report.getRisks().get(0).type());
            assertEquals(1, report.getSuggestions().size());
            assertEquals(FileReviewReport.AnalysisMethod.WHOLE_FILE, report.getAnalysisMethod());
        }
    }

    @Nested
    @DisplayName("mergeRisks() — 风险合并")
    class MergeRisks {

        @Test
        @DisplayName("相同类型的风险应合并")
        void shouldMergeSameTypeRisks() {
            var r1 = new ChunkReviewResult.RiskItem("NullPointer", 10, "NPE at line 10");
            var r2 = new ChunkReviewResult.RiskItem("NullPointer", 20, "NPE at line 20");
            var r3 = new ChunkReviewResult.RiskItem("Security", 30, "SQL injection");

            ChunkReviewResult cr1 = chunkResult("hunk-1", "s1", ChunkReviewResult.RiskLevel.LOW, List.of(r1, r3), null);
            ChunkReviewResult cr2 = chunkResult("hunk-2", "s2", ChunkReviewResult.RiskLevel.LOW, List.of(r2), null);

            List<ChunkReviewResult.RiskItem> merged = reducer.mergeRisks(List.of(cr1, cr2));

            assertEquals(2, merged.size(), "两个 NullPointer 按 type 合并为一条，Security 单独一条");
            assertEquals("NullPointer", merged.get(0).type());
            assertEquals("Security", merged.get(1).type());
        }

        @Test
        @DisplayName("不同类型风险不应合并")
        void shouldNotMergeDifferentTypes() {
            var r1 = new ChunkReviewResult.RiskItem("NullPointer", 10, "NPE");
            var r2 = new ChunkReviewResult.RiskItem("Security", 20, "SQL injection");

            ChunkReviewResult cr = chunkResult("hunk-1", "s", ChunkReviewResult.RiskLevel.LOW, List.of(r1, r2), null);

            assertEquals(2, reducer.mergeRisks(List.of(cr)).size());
        }
    }

    @Nested
    @DisplayName("calculateRiskLevel() — 风险升级")
    class CalculateRiskLevel {

        @Test
        @DisplayName("任一 HIGH → 文件 HIGH")
        void shouldUpgradeToHigh() {
            var r1 = chunkResult("hunk-1", "s1", ChunkReviewResult.RiskLevel.LOW, null, null);
            var r2 = chunkResult("hunk-2", "s2", ChunkReviewResult.RiskLevel.HIGH, null, null);

            assertEquals(FileReviewReport.RiskLevel.HIGH, reducer.calculateRiskLevel(List.of(r1, r2)));
        }

        @Test
        @DisplayName("无 HIGH 有 MEDIUM → 文件 MEDIUM")
        void shouldBeMediumWhenNoHigh() {
            var r1 = chunkResult("hunk-1", "s1", ChunkReviewResult.RiskLevel.LOW, null, null);
            var r2 = chunkResult("hunk-2", "s2", ChunkReviewResult.RiskLevel.MEDIUM, null, null);

            assertEquals(FileReviewReport.RiskLevel.MEDIUM, reducer.calculateRiskLevel(List.of(r1, r2)));
        }

        @Test
        @DisplayName("全 LOW → 文件 LOW")
        void shouldBeLowWhenAllLow() {
            var r1 = chunkResult("hunk-1", "s1", ChunkReviewResult.RiskLevel.LOW, null, null);
            var r2 = chunkResult("hunk-2", "s2", ChunkReviewResult.RiskLevel.LOW, null, null);

            assertEquals(FileReviewReport.RiskLevel.LOW, reducer.calculateRiskLevel(List.of(r1, r2)));
        }
    }

    @Nested
    @DisplayName("buildOverallSummary() — 摘要拼接")
    class BuildOverallSummary {

        @Test
        @DisplayName("单块直接返回摘要")
        void shouldReturnSingleSummary() {
            var r = chunkResult("hunk-1", "Fixed NPE in findById", ChunkReviewResult.RiskLevel.LOW, null, null);
            assertEquals("Fixed NPE in findById", reducer.buildOverallSummary(List.of(r)));
        }

        @Test
        @DisplayName("多块用分号拼接")
        void shouldJoinMultipleSummaries() {
            var r1 = chunkResult("hunk-1", "Fixed NPE", ChunkReviewResult.RiskLevel.LOW, null, null);
            var r2 = chunkResult("hunk-2", "Added validation", ChunkReviewResult.RiskLevel.LOW, null, null);

            String summary = reducer.buildOverallSummary(List.of(r1, r2));
            assertTrue(summary.contains("Fixed NPE"));
            assertTrue(summary.contains("Added validation"));
            assertTrue(summary.contains(";"));
        }

        @Test
        @DisplayName("null 摘要应处理为空")
        void shouldHandleNullSummary() {
            var r = chunkResult("hunk-1", null, ChunkReviewResult.RiskLevel.LOW, null, null);
            assertEquals("", reducer.buildOverallSummary(List.of(r)));
        }
    }

    @Nested
    @DisplayName("mergeSuggestions() — 建议排序")
    class MergeSuggestions {

        @Test
        @DisplayName("应按优先级排序")
        void shouldSortByPriority() {
            var s1 = new ChunkReviewResult.SuggestionItem(3, "low");
            var s2 = new ChunkReviewResult.SuggestionItem(1, "high");
            var s3 = new ChunkReviewResult.SuggestionItem(2, "medium");

            ChunkReviewResult cr = chunkResult("hunk-1", "s", ChunkReviewResult.RiskLevel.LOW, null, null);
            cr.setSuggestions(List.of(s1, s2, s3));

            List<ChunkReviewResult.SuggestionItem> sorted = reducer.mergeSuggestions(List.of(cr));
            assertEquals(1, sorted.get(0).priority());
            assertEquals(2, sorted.get(1).priority());
            assertEquals(3, sorted.get(2).priority());
        }
    }

    @Nested
    @DisplayName("detectCrossChunkIssues() — 跨块一致性")
    class DetectCrossChunkIssues {

        @Test
        @DisplayName("无 crossChunkHints 应返回空")
        void shouldReturnEmptyForNoHints() {
            var r = chunkResult("hunk-1", "s", ChunkReviewResult.RiskLevel.LOW, null, null);
            assertTrue(reducer.detectCrossChunkIssues(List.of(r)).isEmpty());
        }

        @Test
        @DisplayName("有 hints 应生成 Issue")
        void shouldDetectHints() {
            var r1 = chunkResult("hunk-1", "s1", ChunkReviewResult.RiskLevel.LOW, null, "新增了 Optional 导入");
            var r2 = chunkResult("hunk-2", "s2", ChunkReviewResult.RiskLevel.LOW, null, "修改了 findById 签名");

            List<String> issues = reducer.detectCrossChunkIssues(List.of(r1, r2));
            assertEquals(2, issues.size());
            assertTrue(issues.get(0).contains("新增了 Optional"));
            assertTrue(issues.get(1).contains("findById"));
        }
    }

    @Nested
    @DisplayName("analysisMethod — 分析方法判断")
    class AnalysisMethod {

        @Test
        @DisplayName("单块（无 fn 后缀）→ WHOLE_FILE")
        void shouldBeWholeFileForSingleChunk() {
            var r = chunkResult("UserService.java#hunk-1", "s", ChunkReviewResult.RiskLevel.LOW, null, null);
            FileReviewReport report = reducer.reduce(List.of(r), "UserService.java", FileChangeType.MODIFIED);
            assertEquals(FileReviewReport.AnalysisMethod.WHOLE_FILE, report.getAnalysisMethod());
        }

        @Test
        @DisplayName("多块 → CHUNKED")
        void shouldBeChunkedForMultipleChunks() {
            var r1 = chunkResult("UserService.java#hunk-1", "s1", ChunkReviewResult.RiskLevel.LOW, null, null);
            var r2 = chunkResult("UserService.java#hunk-2", "s2", ChunkReviewResult.RiskLevel.LOW, null, null);
            FileReviewReport report = reducer.reduce(List.of(r1, r2), "UserService.java", FileChangeType.MODIFIED);
            assertEquals(FileReviewReport.AnalysisMethod.CHUNKED, report.getAnalysisMethod());
        }

        @Test
        @DisplayName("含 fn 后缀 → CHUNKED")
        void shouldBeChunkedForFunctionSplit() {
            var r = chunkResult("UserService.java#hunk-1-fn-1", "s", ChunkReviewResult.RiskLevel.LOW, null, null);
            FileReviewReport report = reducer.reduce(List.of(r), "UserService.java", FileChangeType.MODIFIED);
            assertEquals(FileReviewReport.AnalysisMethod.CHUNKED, report.getAnalysisMethod());
        }
    }
}
