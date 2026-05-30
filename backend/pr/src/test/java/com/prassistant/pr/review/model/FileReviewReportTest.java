package com.prassistant.pr.review.model;

import com.prassistant.pr.diff.model.FileChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileReviewReportTest {

    @Nested
    @DisplayName("Builder 模式 — 正常构建")
    class Builder {

        @Test
        @DisplayName("应正确设置所有字段")
        void shouldSetAllFieldsViaBuilder() {
            var riskItem = new ChunkReviewResult.RiskItem("NullPointer", 48, "潜在 NPE");
            var suggestionItem = new ChunkReviewResult.SuggestionItem(1, "添加 @Transactional");

            FileReviewReport report = FileReviewReport.builder()
                .filePath("src/main/java/UserService.java")
                .status(FileChangeType.MODIFIED)
                .overallSummary("重构 findById 方法，使用 Optional 替代 null 返回")
                .riskLevel(FileReviewReport.RiskLevel.MEDIUM)
                .risks(List.of(riskItem))
                .suggestions(List.of(suggestionItem))
                .crossChunkIssues(List.of("findById 签名变更，调用方需同步修改"))
                .analysisMethod(FileReviewReport.AnalysisMethod.WHOLE_FILE)
                .build();

            assertEquals("src/main/java/UserService.java", report.getFilePath());
            assertEquals(FileChangeType.MODIFIED, report.getStatus());
            assertEquals("重构 findById 方法，使用 Optional 替代 null 返回", report.getOverallSummary());
            assertEquals(FileReviewReport.RiskLevel.MEDIUM, report.getRiskLevel());
            assertEquals(1, report.getRisks().size());
            assertEquals(1, report.getSuggestions().size());
            assertEquals(1, report.getCrossChunkIssues().size());
            assertEquals(FileReviewReport.AnalysisMethod.WHOLE_FILE, report.getAnalysisMethod());
            assertNull(report.getError());
            assertTrue(report.isSuccess());
        }

        @Test
        @DisplayName("未设置的字段应有默认值")
        void shouldHaveDefaultsForUnsetFields() {
            FileReviewReport report = FileReviewReport.builder()
                .filePath("test.java")
                .build();

            assertEquals("test.java", report.getFilePath());
            assertNull(report.getStatus());
            assertNull(report.getOverallSummary());
            assertNull(report.getRiskLevel());
            assertNull(report.getRisks());
            assertNull(report.getSuggestions());
            assertNull(report.getCrossChunkIssues());
            assertNull(report.getAnalysisMethod());
        }

        @Test
        @DisplayName("错误报告应标记为未成功")
        void shouldMarkAsFailedWhenErrorIsSet() {
            FileReviewReport report = FileReviewReport.builder()
                .filePath("broken.java")
                .error("AI 调用超时")
                .build();

            assertFalse(report.isSuccess());
            assertEquals("AI 调用超时", report.getError());
        }
    }

    @Nested
    @DisplayName("AnalysisMethod 枚举")
    class AnalysisMethodEnum {

        @Test
        @DisplayName("应包含两种分析方式")
        void shouldContainTwoMethods() {
            assertEquals(2, FileReviewReport.AnalysisMethod.values().length);
            assertEquals(FileReviewReport.AnalysisMethod.WHOLE_FILE,
                FileReviewReport.AnalysisMethod.valueOf("WHOLE_FILE"));
            assertEquals(FileReviewReport.AnalysisMethod.CHUNKED,
                FileReviewReport.AnalysisMethod.valueOf("CHUNKED"));
        }
    }

    @Nested
    @DisplayName("RiskLevel 枚举")
    class RiskLevelEnum {

        @Test
        @DisplayName("应按严重程度包含三个等级")
        void shouldContainThreeLevels() {
            assertEquals(3, FileReviewReport.RiskLevel.values().length);
            assertEquals(FileReviewReport.RiskLevel.HIGH,
                FileReviewReport.RiskLevel.valueOf("HIGH"));
            assertEquals(FileReviewReport.RiskLevel.MEDIUM,
                FileReviewReport.RiskLevel.valueOf("MEDIUM"));
            assertEquals(FileReviewReport.RiskLevel.LOW,
                FileReviewReport.RiskLevel.valueOf("LOW"));
        }
    }

    @Nested
    @DisplayName("新增文件场景")
    class AddedFileScenario {

        @Test
        @DisplayName("新增文件应按 ADDED 标记")
        void shouldMarkAsAdded() {
            FileReviewReport report = FileReviewReport.builder()
                .filePath("src/main/java/NewService.java")
                .status(FileChangeType.ADDED)
                .overallSummary("新增 NewService，提供 doSomething 方法")
                .riskLevel(FileReviewReport.RiskLevel.LOW)
                .analysisMethod(FileReviewReport.AnalysisMethod.WHOLE_FILE)
                .build();

            assertEquals(FileChangeType.ADDED, report.getStatus());
            assertTrue(report.isSuccess());
        }
    }

    @Nested
    @DisplayName("equals / hashCode / toString")
    class EqualsHashCodeToString {

        @Test
        @DisplayName("相同字段应 equal")
        void shouldBeEqual() {
            var r1 = FileReviewReport.builder()
                .filePath("test.java")
                .status(FileChangeType.MODIFIED)
                .riskLevel(FileReviewReport.RiskLevel.LOW)
                .analysisMethod(FileReviewReport.AnalysisMethod.WHOLE_FILE)
                .build();
            var r2 = FileReviewReport.builder()
                .filePath("test.java")
                .status(FileChangeType.MODIFIED)
                .riskLevel(FileReviewReport.RiskLevel.LOW)
                .analysisMethod(FileReviewReport.AnalysisMethod.WHOLE_FILE)
                .build();
            assertEquals(r1, r2);
        }

        @Test
        @DisplayName("toString 应包含关键字段")
        void toStringShouldContainKeyFields() {
            var report = FileReviewReport.builder()
                .filePath("UserService.java")
                .status(FileChangeType.MODIFIED)
                .riskLevel(FileReviewReport.RiskLevel.HIGH)
                .analysisMethod(FileReviewReport.AnalysisMethod.CHUNKED)
                .error("timeout")
                .build();
            String str = report.toString();
            assertTrue(str.contains("UserService.java"));
            assertTrue(str.contains("MODIFIED"));
            assertTrue(str.contains("HIGH"));
            assertTrue(str.contains("CHUNKED"));
            assertTrue(str.contains("timeout"));
        }
    }
}
