package com.prassistant.pr.aggregation.model;

import com.prassistant.pr.review.model.FileReviewReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobalReviewReportTest {

    @Nested
    @DisplayName("GlobalReviewReport — 构建")
    class Build {

        @Test
        @DisplayName("应正确构建完整报告")
        void shouldBuildFullReport() {
            GlobalReviewReport report = GlobalReviewReport.builder()
                .taskId("task-001")
                .prUrl("https://github.com/example/pr/1")
                .overallSummary("Add login API with OAuth2")
                .globalRiskLevel(GlobalReviewReport.RiskLevel.MEDIUM)
                .globalRiskReason("涉及支付流程，存在并发风险")
                .crossFileIssues(List.of())
                .architectureSuggestions(List.of("建议抽取公共工具类"))
                .topPriorityFiles(List.of("OrderService.java", "PaymentService.java"))
                .fileReports(List.of())
                .analysisTimeMs(12345L)
                .build();

            assertEquals("task-001", report.getTaskId());
            assertEquals("https://github.com/example/pr/1", report.getPrUrl());
            assertEquals("Add login API with OAuth2", report.getOverallSummary());
            assertEquals(GlobalReviewReport.RiskLevel.MEDIUM, report.getGlobalRiskLevel());
            assertEquals("涉及支付流程，存在并发风险", report.getGlobalRiskReason());
            assertTrue(report.isSuccess());
        }

        @Test
        @DisplayName("无 error 时应标记为成功")
        void shouldBeSuccessWhenNoError() {
            GlobalReviewReport report = GlobalReviewReport.builder()
                .overallSummary("test")
                .build();

            assertTrue(report.isSuccess());
            assertNull(report.getError());
        }

        @Test
        @DisplayName("有 error 时应标记为失败")
        void shouldBeFailureWhenError() {
            GlobalReviewReport report = GlobalReviewReport.builder()
                .overallSummary("test")
                .error("分析失败")
                .build();

            assertFalse(report.isSuccess());
            assertEquals("分析失败", report.getError());
        }

        @Test
        @DisplayName("fileReports 透传第二层报告")
        void shouldPreserveFileReports() {
            FileReviewReport fileReport = FileReviewReport.builder()
                .filePath("Test.java")
                .overallSummary("测试")
                .build();

            GlobalReviewReport report = GlobalReviewReport.builder()
                .fileReports(List.of(fileReport))
                .build();

            assertEquals(1, report.getFileReports().size());
            assertEquals("Test.java", report.getFileReports().get(0).getFilePath());
        }
    }

    @Nested
    @DisplayName("RiskLevel 枚举")
    class RiskLevelEnum {

        @Test
        @DisplayName("应包含三个级别")
        void shouldHaveThreeValues() {
            assertEquals(3, GlobalReviewReport.RiskLevel.values().length);
            assertNotNull(GlobalReviewReport.RiskLevel.valueOf("HIGH"));
            assertNotNull(GlobalReviewReport.RiskLevel.valueOf("MEDIUM"));
            assertNotNull(GlobalReviewReport.RiskLevel.valueOf("LOW"));
        }
    }

    @Nested
    @DisplayName("IssueType 枚举")
    class IssueTypeEnum {

        @Test
        @DisplayName("应包含六种问题类型")
        void shouldHaveSixValues() {
            assertEquals(6, GlobalReviewReport.IssueType.values().length);
            assertNotNull(GlobalReviewReport.IssueType.valueOf("INTERFACE_MISMATCH"));
            assertNotNull(GlobalReviewReport.IssueType.valueOf("DUPLICATE_LOGIC"));
            assertNotNull(GlobalReviewReport.IssueType.valueOf("TRANSACTION_MISSING"));
            assertNotNull(GlobalReviewReport.IssueType.valueOf("SECURITY_PROPAGATION"));
            assertNotNull(GlobalReviewReport.IssueType.valueOf("DB_CODE_INCONSISTENCY"));
            assertNotNull(GlobalReviewReport.IssueType.valueOf("OTHER"));
        }
    }

    @Nested
    @DisplayName("CrossFileIssue — 跨文件关联问题")
    class CrossFileIssue {

        @Test
        @DisplayName("应正确构建")
        void shouldBuildCorrectly() {
            GlobalReviewReport.CrossFileIssue issue = GlobalReviewReport.CrossFileIssue.builder()
                .issueType(GlobalReviewReport.IssueType.INTERFACE_MISMATCH)
                .description("接口 IUserService 新增了 findById，但 UserServiceImpl 未实现")
                .involvedFiles(List.of("IUserService.java", "UserServiceImpl.java"))
                .severity("HIGH")
                .suggestion("请在 UserServiceImpl 中实现 findById 方法")
                .build();

            assertEquals(GlobalReviewReport.IssueType.INTERFACE_MISMATCH, issue.getIssueType());
            assertEquals("接口 IUserService 新增了 findById，但 UserServiceImpl 未实现", issue.getDescription());
            assertEquals(2, issue.getInvolvedFiles().size());
            assertEquals("HIGH", issue.getSeverity());
            assertEquals("请在 UserServiceImpl 中实现 findById 方法", issue.getSuggestion());
        }

        @Test
        @DisplayName("默认值应为零值")
        void shouldHaveDefaultValues() {
            GlobalReviewReport.CrossFileIssue issue = new GlobalReviewReport.CrossFileIssue();

            assertNull(issue.getIssueType());
            assertNull(issue.getDescription());
            assertNull(issue.getInvolvedFiles());
            assertNull(issue.getSeverity());
            assertNull(issue.getSuggestion());
        }
    }
}
