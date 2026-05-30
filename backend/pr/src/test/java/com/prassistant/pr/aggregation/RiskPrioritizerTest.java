package com.prassistant.pr.aggregation;

import com.prassistant.pr.aggregation.model.GlobalReviewReport;
import com.prassistant.pr.diff.model.FileChangeType;
import com.prassistant.pr.review.model.FileReviewReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RiskPrioritizerTest {

    private FileReviewReport fileReport(String path, FileReviewReport.RiskLevel risk) {
        return FileReviewReport.builder()
            .filePath(path)
            .status(FileChangeType.MODIFIED)
            .overallSummary("test")
            .riskLevel(risk)
            .build();
    }

    @Nested
    @DisplayName("prioritize() — 主入口")
    class Prioritize {

        @Test
        @DisplayName("null 输入返回 null")
        void shouldReturnNullForNullInput() {
            assertNull(RiskPrioritizer.prioritize(null));
        }

        @Test
        @DisplayName("空报告应正常返回")
        void shouldHandleEmptyReport() {
            GlobalReviewReport report = GlobalReviewReport.builder()
                .overallSummary("test")
                .build();

            GlobalReviewReport result = RiskPrioritizer.prioritize(report);

            assertNotNull(result);
            assertEquals("test", result.getOverallSummary());
        }
    }

    @Nested
    @DisplayName("dedupCrossFileIssues() — 去重合并")
    class DedupIssues {

        @Test
        @DisplayName("相似描述应合并")
        void shouldMergeSimilarDescriptions() {
            var issue1 = GlobalReviewReport.CrossFileIssue.builder()
                .issueType(GlobalReviewReport.IssueType.INTERFACE_MISMATCH)
                .description("IUserService 新增了 findById 接口方法 但 Impl 未实现")
                .involvedFiles(List.of("IUserService.java"))
                .build();

            var issue2 = GlobalReviewReport.CrossFileIssue.builder()
                .issueType(GlobalReviewReport.IssueType.INTERFACE_MISMATCH)
                .description("IUserService 新增了 findById 接口方法 但 Impl 中没有实现")
                .involvedFiles(List.of("UserServiceImpl.java"))
                .build();

            List<GlobalReviewReport.CrossFileIssue> result =
                RiskPrioritizer.dedupCrossFileIssues(List.of(issue1, issue2));

            assertEquals(1, result.size(), "相似问题应合并为一条");
            // 合并后应包含两个文件的路径
            assertTrue(result.get(0).getInvolvedFiles().contains("IUserService.java"));
            assertTrue(result.get(0).getInvolvedFiles().contains("UserServiceImpl.java"));
        }

        @Test
        @DisplayName("不同描述不应合并")
        void shouldNotMergeDifferentDescriptions() {
            var issue1 = GlobalReviewReport.CrossFileIssue.builder()
                .issueType(GlobalReviewReport.IssueType.INTERFACE_MISMATCH)
                .description("IUserService 新增了 findById")
                .involvedFiles(List.of("IUserService.java"))
                .build();

            var issue2 = GlobalReviewReport.CrossFileIssue.builder()
                .issueType(GlobalReviewReport.IssueType.DUPLICATE_LOGIC)
                .description("多个文件存在 SQL 拼接逻辑")
                .involvedFiles(List.of("Mapper.xml"))
                .build();

            List<GlobalReviewReport.CrossFileIssue> result =
                RiskPrioritizer.dedupCrossFileIssues(List.of(issue1, issue2));

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("null/空列表返回原值")
        void shouldReturnOriginalForNull() {
            assertNull(RiskPrioritizer.dedupCrossFileIssues(null));
            assertTrue(RiskPrioritizer.dedupCrossFileIssues(List.of()).isEmpty());
        }
    }

    @Nested
    @DisplayName("calibrateRiskLevel() — 风险校准")
    class CalibrateRisk {

        @Test
        @DisplayName("核心流程文件 HIGH + 全局 LOW → 升级 MEDIUM")
        void shouldUpgradeForCoreFlowHighRisk() {
            var files = List.of(
                fileReport("OrderService.java", FileReviewReport.RiskLevel.HIGH)
            );

            GlobalReviewReport.RiskLevel result = RiskPrioritizer.calibrateRiskLevel(
                GlobalReviewReport.RiskLevel.LOW, files);

            assertEquals(GlobalReviewReport.RiskLevel.MEDIUM, result);
        }

        @Test
        @DisplayName("非核心流程文件 HIGH + 全局 LOW → 不升级")
        void shouldNotUpgradeForNonCoreFile() {
            var files = List.of(
                fileReport("LoggerUtil.java", FileReviewReport.RiskLevel.HIGH)
            );

            GlobalReviewReport.RiskLevel result = RiskPrioritizer.calibrateRiskLevel(
                GlobalReviewReport.RiskLevel.LOW, files);

            assertEquals(GlobalReviewReport.RiskLevel.LOW, result);
        }

        @Test
        @DisplayName("已经是 HIGH 不降级")
        void shouldNotDowngradeHigh() {
            var files = List.of(
                fileReport("HelloService.java", FileReviewReport.RiskLevel.MEDIUM)
            );

            GlobalReviewReport.RiskLevel result = RiskPrioritizer.calibrateRiskLevel(
                GlobalReviewReport.RiskLevel.HIGH, files);

            assertEquals(GlobalReviewReport.RiskLevel.HIGH, result);
        }
    }

    @Nested
    @DisplayName("computeTopPriorityFiles() — 优先文件排序")
    class TopPriorityFiles {

        @Test
        @DisplayName("应返回风险最高的前 3 个文件")
        void shouldReturnTop3() {
            var files = List.of(
                fileReport("A.java", FileReviewReport.RiskLevel.LOW),
                fileReport("B.java", FileReviewReport.RiskLevel.HIGH),
                fileReport("C.java", FileReviewReport.RiskLevel.MEDIUM),
                fileReport("D.java", FileReviewReport.RiskLevel.LOW)
            );

            List<String> top = RiskPrioritizer.computeTopPriorityFiles(files);

            assertEquals(3, top.size());
            assertEquals("B.java", top.get(0), "HIGH 优先");
            assertEquals("C.java", top.get(1), "MEDIUM 次之");
        }

        @Test
        @DisplayName("空列表返回空")
        void shouldReturnEmptyForEmptyList() {
            assertTrue(RiskPrioritizer.computeTopPriorityFiles(List.of()).isEmpty());
        }
    }

    @Nested
    @DisplayName("truncateSummary() — 截断保护")
    class TruncateSummary {

        @Test
        @DisplayName("短文本不截断")
        void shouldNotTruncateShortText() {
            assertEquals("修复订单并发扣减问题",
                RiskPrioritizer.truncateSummary("修复订单并发扣减问题"));
        }

        @Test
        @DisplayName("超长文本截断到 80 字")
        void shouldTruncateLongText() {
            String longText = "a".repeat(200);
            String result = RiskPrioritizer.truncateSummary(longText);

            assertEquals(80, result.length());
            assertTrue(result.endsWith("..."));
        }

        @Test
        @DisplayName("null 返回空字符串")
        void shouldReturnEmptyForNull() {
            assertEquals("", RiskPrioritizer.truncateSummary(null));
        }
    }

    @Nested
    @DisplayName("wordOverlap() — 词重叠率")
    class WordOverlap {

        @Test
        @DisplayName("完全相同返回 1.0")
        void shouldReturnOneForIdentical() {
            assertEquals(1.0, RiskPrioritizer.wordOverlap("hello world", "hello world"), 0.01);
        }

        @Test
        @DisplayName("完全不同返回 0.0")
        void shouldReturnZeroForDifferent() {
            assertEquals(0.0, RiskPrioritizer.wordOverlap("hello world", "foo bar"), 0.01);
        }

        @Test
        @DisplayName("部分重叠应在 0-1 之间")
        void shouldReturnPartialForPartialOverlap() {
            double overlap = RiskPrioritizer.wordOverlap(
                "IUserService 新增 findById 方法",
                "IUserService 添加 findById 接口");

            assertTrue(overlap > 0.2 && overlap < 0.5,
                "Expected overlap around 0.33 but got: " + overlap);
        }
    }

    @Nested
    @DisplayName("isCoreFlowFile() — 核心流程检测")
    class IsCoreFlowFile {

        @Test
        @DisplayName("包含支付/订单关键词返回 true")
        void shouldReturnTrueForCoreFlow() {
            assertTrue(RiskPrioritizer.isCoreFlowFile("OrderService.java"));
            assertTrue(RiskPrioritizer.isCoreFlowFile("PaymentController.java"));
            assertTrue(RiskPrioritizer.isCoreFlowFile("InventoryManager.java"));
        }

        @Test
        @DisplayName("不包含关键词返回 false")
        void shouldReturnFalseForNonCoreFlow() {
            assertFalse(RiskPrioritizer.isCoreFlowFile("UserService.java"));
            assertFalse(RiskPrioritizer.isCoreFlowFile("LoggerUtil.java"));
        }

        @Test
        @DisplayName("null 返回 false")
        void shouldReturnFalseForNull() {
            assertFalse(RiskPrioritizer.isCoreFlowFile(null));
        }
    }
}
