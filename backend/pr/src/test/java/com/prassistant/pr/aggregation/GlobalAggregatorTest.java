package com.prassistant.pr.aggregation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prassistant.pr.aggregation.model.GlobalReviewReport;
import com.prassistant.pr.aggregation.model.PrMetadata;
import com.prassistant.pr.diff.model.FileChangeType;
import com.prassistant.pr.review.model.FileReviewReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalAggregatorTest {

    private ChatClient.Builder chatClientBuilder;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;
    private ObjectMapper objectMapper;
    private GlobalAggregator aggregator;

    @BeforeEach
    void setUp() {
        chatClientBuilder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        objectMapper = new ObjectMapper();

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);

        aggregator = new GlobalAggregator(chatClientBuilder, objectMapper);
    }

    private FileReviewReport sampleReport(String path, String summary) {
        return FileReviewReport.builder()
            .filePath(path)
            .status(FileChangeType.MODIFIED)
            .overallSummary(summary)
            .analysisMethod(FileReviewReport.AnalysisMethod.WHOLE_FILE)
            .build();
    }

    private PrMetadata sampleMetadata() {
        return PrMetadata.builder()
            .title("test PR")
            .baseBranch("main")
            .headBranch("feat/test")
            .totalFiles(1)
            .build();
    }

    @Nested
    @DisplayName("aggregate() — 主入口")
    class Aggregate {

        @Test
        @DisplayName("空文件列表返回错误报告")
        void shouldReturnErrorForEmptyFiles() {
            GlobalReviewReport report = aggregator.aggregate(List.of(), sampleMetadata());

            assertFalse(report.isSuccess());
            assertNotNull(report.getError());
        }

        @Test
        @DisplayName("null 文件列表返回错误报告")
        void shouldReturnErrorForNullFiles() {
            GlobalReviewReport report = aggregator.aggregate(null, sampleMetadata());

            assertFalse(report.isSuccess());
        }

        @Test
        @DisplayName("AI 返回空响应返回错误报告")
        void shouldReturnErrorForEmptyAiResponse() {
            when(callSpec.content()).thenReturn("");

            GlobalReviewReport report = aggregator.aggregate(
                List.of(sampleReport("Test.java", "test")), sampleMetadata());

            assertFalse(report.isSuccess());
        }

        @Test
        @DisplayName("成功流程应返回完整报告")
        void shouldReturnCompleteReport() {
            String jsonResponse = """
                {
                    "overallSummary": "新增登录功能，涉及 Controller/Service/Mapper 三层",
                    "globalRiskLevel": "LOW",
                    "globalRiskReason": "变更范围小，逻辑清晰",
                    "crossFileIssues": [],
                    "architectureSuggestions": ["建议添加统一异常处理"],
                    "topPriorityFiles": ["AuthController.java"]
                }
                """;
            when(callSpec.content()).thenReturn(jsonResponse);

            GlobalReviewReport report = aggregator.aggregate(
                List.of(sampleReport("AuthController.java", "新增登录接口")),
                sampleMetadata());

            assertTrue(report.isSuccess());
            assertNotNull(report.getTaskId());
            assertNotNull(report.getOverallSummary());
            assertEquals(GlobalReviewReport.RiskLevel.LOW, report.getGlobalRiskLevel());
            assertTrue(report.getAnalysisTimeMs() >= 0);
            assertEquals(1, report.getFileReports().size());
        }

        @Test
        @DisplayName("AI 异常应返回错误报告")
        void shouldReturnErrorOnAiException() {
            when(callSpec.content()).thenThrow(new RuntimeException("API timeout"));

            GlobalReviewReport report = aggregator.aggregate(
                List.of(sampleReport("Test.java", "test")), sampleMetadata());

            assertFalse(report.isSuccess());
            assertTrue(report.getError().contains("API timeout"));
        }
    }

    @Nested
    @DisplayName("parseResponse() — JSON 解析")
    class ParseResponse {

        @Test
        @DisplayName("完整 JSON 应正确解析")
        void shouldParseFullJson() {
            String json = """
                {
                    "overallSummary": "修复订单并发扣减问题",
                    "globalRiskLevel": "HIGH",
                    "globalRiskReason": "涉及支付流程，并发控制不足",
                    "crossFileIssues": [
                        {
                            "issueType": "TRANSACTION_MISSING",
                            "description": "订单扣减和支付操作不在同一事务",
                            "involvedFiles": ["OrderService.java", "PaymentService.java"],
                            "severity": "HIGH",
                            "suggestion": "添加 @Transactional 注解"
                        }
                    ],
                    "architectureSuggestions": ["建议引入分布式事务"],
                    "topPriorityFiles": ["OrderService.java", "PaymentService.java"]
                }
                """;

            var reports = List.of(sampleReport("OrderService.java", "test"));
            GlobalReviewReport report = aggregator.parseResponse(json, reports);

            assertEquals("修复订单并发扣减问题", report.getOverallSummary());
            assertEquals(GlobalReviewReport.RiskLevel.HIGH, report.getGlobalRiskLevel());
            assertEquals("涉及支付流程，并发控制不足", report.getGlobalRiskReason());

            assertEquals(1, report.getCrossFileIssues().size());
            assertEquals(GlobalReviewReport.IssueType.TRANSACTION_MISSING,
                report.getCrossFileIssues().get(0).getIssueType());
            assertEquals(2, report.getCrossFileIssues().get(0).getInvolvedFiles().size());

            assertEquals(1, report.getArchitectureSuggestions().size());
            assertEquals(2, report.getTopPriorityFiles().size());
        }

        @Test
        @DisplayName("无效 JSON 返回错误报告")
        void shouldReturnErrorForInvalidJson() {
            var reports = List.of(sampleReport("Test.java", "test"));
            GlobalReviewReport report = aggregator.parseResponse(
                "not valid json", reports);

            assertFalse(report.isSuccess());
        }

        @Test
        @DisplayName("空 JSON 应解析为默认值")
        void shouldHandleEmptyJson() {
            var reports = List.of(sampleReport("Test.java", "test"));
            GlobalReviewReport report = aggregator.parseResponse(
                "{}", reports);

            assertNotNull(report.getOverallSummary());
            assertNull(report.getGlobalRiskLevel());
        }

        @Test
        @DisplayName("不支持的 riskLevel 应默认 LOW")
        void shouldDefaultToLowForInvalidRiskLevel() {
            String json = """
                { "globalRiskLevel": "INVALID", "overallSummary": "test" }
                """;
            var reports = List.of(sampleReport("Test.java", "test"));
            GlobalReviewReport report = aggregator.parseResponse(json, reports);

            assertEquals(GlobalReviewReport.RiskLevel.LOW, report.getGlobalRiskLevel());
        }
    }
}
