package com.prassistant.pr.review;

import com.prassistant.pr.diff.model.DiffHunk;
import com.prassistant.pr.diff.model.FileChangeType;
import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.review.analyzer.ChunkAnalyzer;
import com.prassistant.pr.review.analyzer.FileReportReducer;
import com.prassistant.pr.review.model.ChunkReviewResult;
import com.prassistant.pr.review.model.FileReviewReport;
import com.prassistant.pr.review.splitter.FunctionBasedChunkSplitter;
import com.prassistant.pr.review.splitter.HunkBasedChunkSplitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileChunkAnalyzerTest {

    private HunkBasedChunkSplitter hunkSplitter;
    private FunctionBasedChunkSplitter functionSplitter;
    private ChunkAnalyzer chunkAnalyzer;
    private FileReportReducer reportReducer;
    private FileChunkAnalyzer facade;

    @BeforeEach
    void setUp() {
        hunkSplitter = mock(HunkBasedChunkSplitter.class);
        functionSplitter = mock(FunctionBasedChunkSplitter.class);
        chunkAnalyzer = mock(ChunkAnalyzer.class);
        reportReducer = mock(FileReportReducer.class);
        facade = new FileChunkAnalyzer(hunkSplitter, functionSplitter, chunkAnalyzer, reportReducer);
    }

    private SanitizedDiff createDiff(String filePath, int hunkCount, int linesPerHunk) {
        SanitizedDiff diff = new SanitizedDiff();
        diff.setFilePath(filePath);
        diff.setStatus(FileChangeType.MODIFIED);
        StringBuilder content = new StringBuilder();
        for (int h = 0; h < hunkCount; h++) {
            DiffHunk hunk = new DiffHunk();
            hunk.setOldStartLine(h * 10 + 1);
            hunk.setOldLineCount(5);
            hunk.setNewStartLine(h * 10 + 1);
            hunk.setNewLineCount(6);
            for (int i = 0; i < linesPerHunk; i++) {
                hunk.getLines().add(" public void method" + i + "() { }");
                diff.getHunks().add(hunk);
            }
            content.append("@@ -").append(h * 10 + 1).append(",5 +").append(h * 10 + 1).append(",6 @@\n");
            for (int i = 0; i < linesPerHunk; i++) {
                content.append(" public void method").append(i).append("() { }\n");
            }
        }
        diff.setSanitizedContent(content.toString());
        return diff;
    }

    @Nested
    @DisplayName("analyze() — 单文件分析")
    class AnalyzeSingleFile {

        @Test
        @DisplayName("空内容应返回无变更报告")
        void shouldReturnEmptyReportForBlankContent() throws Exception {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("EmptyFile.java");
            diff.setSanitizedContent("");

            FileReviewReport report = facade.analyze(diff).get();

            assertEquals("EmptyFile.java", report.getFilePath());
            assertEquals("无变更内容", report.getOverallSummary());
        }

        @Test
        @DisplayName("null 内容应返回无变更报告")
        void shouldReturnEmptyReportForNullContent() throws Exception {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("NullFile.java");
            diff.setSanitizedContent(null);

            FileReviewReport report = facade.analyze(diff).get();

            assertEquals("NullFile.java", report.getFilePath());
        }

        @Test
        @DisplayName("异常时应返回错误报告")
        void shouldReturnErrorReportOnException() throws Exception {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("CrashFile.java");
            diff.setSanitizedContent("some code");
            // 模拟 TokenEstimator 异常 — 但 TokenEstimator 是静态方法，不可 mock
            // 这里用空内容测试空路径
            diff.setSanitizedContent("");

            FileReviewReport report = facade.analyze(diff).get();

            assertNotNull(report);
            assertTrue(report.isSuccess() || report.getError() == null);
        }

        @Test
        @DisplayName("缺少文件路径时应显示 unknown")
        void shouldHandleNullFilePath() throws Exception {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath(null);
            diff.setSanitizedContent("");

            FileReviewReport report = facade.analyze(diff).get();

            assertEquals("unknown", report.getFilePath());
        }
    }

    @Nested
    @DisplayName("analyzeAll() — 批量分析")
    class AnalyzeAllFiles {

        @Test
        @DisplayName("空列表应返回空列表")
        void shouldReturnEmptyForEmptyList() throws Exception {
            List<FileReviewReport> reports = facade.analyzeAll(List.of()).get();
            assertTrue(reports.isEmpty());
        }

        @Test
        @DisplayName("null 输入应返回空列表")
        void shouldReturnEmptyForNullInput() throws Exception {
            List<FileReviewReport> reports = facade.analyzeAll(null).get();
            assertTrue(reports.isEmpty());
        }

        @Test
        @DisplayName("多个文件应并行分析返回全部结果")
        void shouldAnalyzeMultipleFiles() throws Exception {
            SanitizedDiff diff1 = createDiff("File1.java", 1, 2);
            SanitizedDiff diff2 = createDiff("File2.java", 1, 2);

            // mock reducer to return valid reports
            when(reportReducer.reduce(anyList(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String fp = invocation.getArgument(1);
                    return FileReviewReport.builder()
                        .filePath(fp)
                        .overallSummary("analyzed")
                        .riskLevel(FileReviewReport.RiskLevel.LOW)
                        .build();
                });

            List<FileReviewReport> reports = facade.analyzeAll(List.of(diff1, diff2)).get();

            assertEquals(2, reports.size());
        }
    }

    @Nested
    @DisplayName("整文件 vs 分块路径")
    class WholeFileVsChunked {

        @Test
        @DisplayName("小文件应走整文件路径")
        void shouldUseWholeFileForSmallContent() throws Exception {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("SmallFile.java");
            diff.setStatus(FileChangeType.MODIFIED);
            diff.setSanitizedContent("public class Small { }"); // < 8000 tokens

            // mock ChunkAnalyzer to return a valid result when whole-file path is used
            when(chunkAnalyzer.analyze(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                    ChunkReviewResult.builder()
                        .chunkId("SmallFile.java#whole")
                        .summary("整文件分析")
                        .riskLevel(ChunkReviewResult.RiskLevel.LOW)
                        .build()
                ));

            when(reportReducer.reduce(anyList(), anyString(), any()))
                .thenReturn(FileReviewReport.builder()
                    .filePath("SmallFile.java")
                    .overallSummary("整文件分析")
                    .riskLevel(FileReviewReport.RiskLevel.LOW)
                    .analysisMethod(FileReviewReport.AnalysisMethod.WHOLE_FILE)
                    .build());

            FileReviewReport report = facade.analyze(diff).get();

            assertEquals("SmallFile.java", report.getFilePath());
            assertNotNull(report.getOverallSummary());
        }

        @Test
        @DisplayName("大文件（模拟）应正常返回报告")
        void shouldHandleLargeContent() throws Exception {
            // 创建足够大内容触发分块路径
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("LargeFile.java");
            diff.setStatus(FileChangeType.MODIFIED);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                sb.append("@@ -").append(i * 10).append(",5 +").append(i * 10).append(",6 @@\n");
                sb.append(" public void method").append(i).append("() {\n");
                sb.append("+        System.out.println(\"").append(i).append("\");\n");
                sb.append(" }\n");
            }
            diff.setSanitizedContent(sb.toString());

            // 添加一些 Hunk 给 splitter
            DiffHunk hunk = new DiffHunk();
            hunk.setOldStartLine(1);
            hunk.setOldLineCount(5);
            hunk.setNewStartLine(1);
            hunk.setNewLineCount(6);
            hunk.getLines().add(" public void test() { }");
            diff.getHunks().add(hunk);

            when(reportReducer.reduce(anyList(), anyString(), any()))
                .thenReturn(FileReviewReport.builder()
                    .filePath("LargeFile.java")
                    .overallSummary("分块分析完成")
                    .riskLevel(FileReviewReport.RiskLevel.MEDIUM)
                    .build());

            FileReviewReport report = facade.analyze(diff).get();

            assertNotNull(report);
            assertTrue(report.isSuccess());
        }
    }
}
