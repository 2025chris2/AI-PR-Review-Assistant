package com.prassistant.pr.aggregation;

import com.prassistant.pr.aggregation.model.PrMetadata;
import com.prassistant.pr.diff.model.FileChangeType;
import com.prassistant.pr.review.model.FileReviewReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobalPromptBuilderTest {

    private PrMetadata sampleMetadata() {
        return PrMetadata.builder()
            .title("feat: add login API")
            .description("Add OAuth2 login endpoint")
            .author("chris")
            .baseBranch("main")
            .headBranch("feat/login")
            .totalFiles(2)
            .totalAdditions(80)
            .totalDeletions(10)
            .changedFileTypes(List.of("Java: 2"))
            .build();
    }

    private FileReviewReport sampleReport(String path, String summary) {
        return FileReviewReport.builder()
            .filePath(path)
            .status(FileChangeType.MODIFIED)
            .overallSummary(summary)
            .analysisMethod(FileReviewReport.AnalysisMethod.WHOLE_FILE)
            .build();
    }

    @Nested
    @DisplayName("build() — 基本功能")
    class Build {

        @Test
        @DisplayName("应包含系统角色设定")
        void shouldContainSystemRole() {
            String prompt = GlobalPromptBuilder.build(
                List.of(), sampleMetadata(), List.of());

            assertTrue(prompt.contains("资深的代码评审专家"));
            assertTrue(prompt.contains("全局评审分析"));
        }

        @Test
        @DisplayName("应包含 PR 元数据")
        void shouldContainPrMetadata() {
            String prompt = GlobalPromptBuilder.build(
                List.of(), sampleMetadata(), List.of());

            assertTrue(prompt.contains("Add OAuth2 login endpoint"));
            assertTrue(prompt.contains("feat/login → main"));
            assertTrue(prompt.contains("chris"));
            assertTrue(prompt.contains("80"));
            assertTrue(prompt.contains("10"));
            assertTrue(prompt.contains("Java: 2"));
        }

        @Test
        @DisplayName("应包含文件分析摘要")
        void shouldContainFileSummaries() {
            var reports = List.of(
                sampleReport("UserService.java", "新增 findByEmail"),
                sampleReport("OrderService.java", "优化查询性能")
            );

            String prompt = GlobalPromptBuilder.build(reports, sampleMetadata(), List.of());

            assertTrue(prompt.contains("UserService.java"));
            assertTrue(prompt.contains("OrderService.java"));
            assertTrue(prompt.contains("新增 findByEmail"));
            assertTrue(prompt.contains("优化查询性能"));
        }

        @Test
        @DisplayName("应包含跨文件线索")
        void shouldContainCrossFileHints() {
            var hints = List.of("接口 IUserService 被修改，请检查 UserServiceImpl");

            String prompt = GlobalPromptBuilder.build(
                List.of(), sampleMetadata(), hints);

            assertTrue(prompt.contains("接口 IUserService"));
            assertTrue(prompt.contains("UserServiceImpl"));
        }

        @Test
        @DisplayName("无跨文件线索时显示占位文本")
        void shouldShowPlaceholderWhenNoHints() {
            String prompt = GlobalPromptBuilder.build(
                List.of(), sampleMetadata(), List.of());

            assertTrue(prompt.contains("未发现明显的跨文件关联"));
        }

        @Test
        @DisplayName("应包含 JSON 输出格式要求")
        void shouldContainOutputFormat() {
            String prompt = GlobalPromptBuilder.build(
                List.of(), sampleMetadata(), List.of());

            assertTrue(prompt.contains("overallSummary"));
            assertTrue(prompt.contains("globalRiskLevel"));
            assertTrue(prompt.contains("crossFileIssues"));
            assertTrue(prompt.contains("architectureSuggestions"));
            assertTrue(prompt.contains("topPriorityFiles"));
        }
    }

    @Nested
    @DisplayName("build() — 边界情况")
    class EdgeCases {

        @Test
        @DisplayName("null metadata 不应抛异常")
        void shouldHandleNullMetadata() {
            assertDoesNotThrow(() ->
                GlobalPromptBuilder.build(List.of(), null, List.of()));
        }

        @Test
        @DisplayName("null reports 不应抛异常")
        void shouldHandleNullReports() {
            assertDoesNotThrow(() ->
                GlobalPromptBuilder.build(null, sampleMetadata(), List.of()));
        }

        @Test
        @DisplayName("null hints 不应抛异常")
        void shouldHandleNullHints() {
            assertDoesNotThrow(() ->
                GlobalPromptBuilder.build(List.of(), sampleMetadata(), null));
        }

        @Test
        @DisplayName("文件含风险和建议时也应正常拼接")
        void shouldHandleReportsWithRisksAndSuggestions() {
            String path = "PaymentService.java";
            FileReviewReport report = FileReviewReport.builder()
                .filePath(path)
                .status(FileChangeType.MODIFIED)
                .overallSummary("修复并发扣减问题")
                .risks(List.of(
                    new com.prassistant.pr.review.model.ChunkReviewResult.RiskItem(
                        "Concurrency", 42, "扣减操作未加锁")))
                .suggestions(List.of(
                    new com.prassistant.pr.review.model.ChunkReviewResult.SuggestionItem(
                        1, "添加 synchronized 或锁机制")))
                .analysisMethod(FileReviewReport.AnalysisMethod.WHOLE_FILE)
                .build();

            String prompt = GlobalPromptBuilder.build(
                List.of(report), sampleMetadata(), List.of());

            assertTrue(prompt.contains("Concurrency"));
            assertTrue(prompt.contains("扣减操作未加锁"));
            assertTrue(prompt.contains("添加 synchronized"));
        }
    }
}
