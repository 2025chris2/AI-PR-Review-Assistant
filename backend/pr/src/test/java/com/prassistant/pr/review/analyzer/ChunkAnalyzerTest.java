package com.prassistant.pr.review.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prassistant.pr.review.model.ChunkReviewResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChunkAnalyzerTest {

    private ChatClient.Builder chatClientBuilder;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;
    private ObjectMapper objectMapper;
    private ChunkAnalyzer analyzer;

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

        analyzer = new ChunkAnalyzer(chatClientBuilder, objectMapper);
    }

    @Nested
    @DisplayName("analyze() — AI 调用")
    class Analyze {

        @Test
        @DisplayName("应正确发送 prompt 并返回结构化结果")
        void shouldSendPromptAndReturnStructuredResult() throws Exception {
            String jsonResponse = """
                {
                    "summary": "修改了 findById 方法，引入 Optional 返回",
                    "risks": [
                        { "type": "NullPointer", "line": 48, "description": "orElseThrow 可能抛异常" }
                    ],
                    "suggestions": [
                        { "priority": 1, "description": "建议添加 @Transactional" }
                    ],
                    "riskLevel": "MEDIUM",
                    "crossChunkHints": "新增了 Optional 导入"
                }
                """;
            when(callSpec.content()).thenReturn(jsonResponse);

            ChunkReviewResult result = analyzer.analyze("test prompt", "test#hunk-1").get();

            verify(requestSpec).user("test prompt");
            assertEquals("test#hunk-1", result.getChunkId());
            assertEquals("修改了 findById 方法，引入 Optional 返回", result.getSummary());
            assertEquals(ChunkReviewResult.RiskLevel.MEDIUM, result.getRiskLevel());
            assertEquals(1, result.getRisks().size());
            assertEquals("NullPointer", result.getRisks().get(0).type());
            assertEquals(48, result.getRisks().get(0).line());
            assertEquals(1, result.getSuggestions().size());
            assertEquals(1, result.getSuggestions().get(0).priority());
            assertEquals("新增了 Optional 导入", result.getCrossChunkHints());
        }

        @Test
        @DisplayName("AI 返回空时应返回错误结果")
        void shouldReturnErrorResultWhenAiReturnsEmpty() throws Exception {
            when(callSpec.content()).thenReturn("");

            ChunkReviewResult result = analyzer.analyze("prompt", "empty#hunk-1").get();

            assertEquals("empty#hunk-1", result.getChunkId());
            assertTrue(result.getSummary().contains("empty"));
            assertEquals(ChunkReviewResult.RiskLevel.LOW, result.getRiskLevel());
        }

        @Test
        @DisplayName("AI 返回 null 时应返回错误结果")
        void shouldReturnErrorResultWhenAiReturnsNull() throws Exception {
            when(callSpec.content()).thenReturn(null);

            ChunkReviewResult result = analyzer.analyze("prompt", "null#hunk-1").get();

            assertEquals("null#hunk-1", result.getChunkId());
        }

        @Test
        @DisplayName("AI 抛出异常时应返回错误结果")
        void shouldHandleAiException() throws Exception {
            when(callSpec.content()).thenThrow(new RuntimeException("API timeout"));

            ChunkReviewResult result = analyzer.analyze("prompt", "error#hunk-1").get();

            assertEquals("error#hunk-1", result.getChunkId());
            assertTrue(result.getSummary().contains("API timeout"));
            assertEquals(ChunkReviewResult.RiskLevel.LOW, result.getRiskLevel());
        }
    }

    @Nested
    @DisplayName("parseResponse() — JSON 解析")
    class ParseResponse {

        @Test
        @DisplayName("标准完整 JSON 应正确解析")
        void shouldParseCompleteJson() {
            String json = """
                {
                    "summary": "test summary",
                    "risks": [
                        { "type": "Security", "line": 10, "description": "SQL injection risk" }
                    ],
                    "suggestions": [
                        { "priority": 1, "description": "use prepared statement" }
                    ],
                    "riskLevel": "HIGH"
                }
                """;

            ChunkReviewResult result = analyzer.parseResponse(json, "test#hunk-1");

            assertEquals("test#hunk-1", result.getChunkId());
            assertEquals("test summary", result.getSummary());
            assertEquals(ChunkReviewResult.RiskLevel.HIGH, result.getRiskLevel());
            assertEquals(1, result.getRisks().size());
            assertEquals("Security", result.getRisks().get(0).type());
            assertEquals("use prepared statement", result.getSuggestions().get(0).description());
        }

        @Test
        @DisplayName("缺少字段时应使用默认值")
        void shouldUseDefaultsForMissingFields() {
            String json = "{\"summary\": \"minimal\"}";

            ChunkReviewResult result = analyzer.parseResponse(json, "minimal#hunk-1");

            assertEquals("minimal#hunk-1", result.getChunkId());
            assertEquals("minimal", result.getSummary());
            assertNull(result.getRisks());
            assertNull(result.getSuggestions());
            assertNull(result.getRiskLevel());
        }

        @Test
        @DisplayName("无效 riskLevel 应降级为 LOW")
        void shouldDefaultRiskLevelForInvalidValue() {
            String json = "{\"summary\": \"test\", \"riskLevel\": \"INVALID\"}";

            ChunkReviewResult result = analyzer.parseResponse(json, "test#hunk-1");

            assertEquals(ChunkReviewResult.RiskLevel.LOW, result.getRiskLevel());
        }

        @Test
        @DisplayName("无效 JSON 应返回错误结果")
        void shouldReturnErrorForInvalidJson() {
            String json = "not valid json";

            ChunkReviewResult result = analyzer.parseResponse(json, "bad#hunk-1");

            assertEquals("bad#hunk-1", result.getChunkId());
            assertTrue(result.getSummary().contains("Invalid JSON"));
        }

        @Test
        @DisplayName("risks 和 suggestions 为空数组时应处理")
        void shouldHandleEmptyArrays() {
            String json = """
                {
                    "summary": "empty arrays",
                    "risks": [],
                    "suggestions": [],
                    "riskLevel": "LOW"
                }
                """;

            ChunkReviewResult result = analyzer.parseResponse(json, "empty#hunk-1");

            assertNotNull(result.getRisks());
            assertTrue(result.getRisks().isEmpty());
            assertNotNull(result.getSuggestions());
            assertTrue(result.getSuggestions().isEmpty());
            assertEquals(ChunkReviewResult.RiskLevel.LOW, result.getRiskLevel());
        }
    }
}
