package com.prassistant.pr.review.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prassistant.pr.review.model.ChunkReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Map 阶段执行器 — 调用 AI 分析单个 Chunk，返回结构化结果
 *
 * <p>使用 Spring AI {@link ChatClient} 调用 AI，解析 JSON 响应为 {@link ChunkReviewResult}。
 * 异步执行（{@link Async}），支持文件级并行分析。</p>
 */
@Service
public class ChunkAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ChunkAnalyzer.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ChunkAnalyzer(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 异步分析单个 Chunk
     *
     * @param prompt 由 {@link ChunkPromptBuilder} 构建的完整 Prompt
     * @param chunkId Chunk 标识（用于日志关联）
     * @return 异步返回结构化分析结果
     */
    @Async
    public CompletableFuture<ChunkReviewResult> analyze(String prompt, String chunkId) {
        try {
            log.debug("Analyzing chunk: {}", chunkId);
            String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

            if (response == null || response.isBlank()) {
                log.warn("AI returned empty response for chunk: {}", chunkId);
                return CompletableFuture.completedFuture(buildErrorResult(chunkId, "AI returned empty response"));
            }

            ChunkReviewResult result = parseResponse(response, chunkId);
            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("AI analysis failed for chunk {}: {}", chunkId, e.getMessage());
            return CompletableFuture.completedFuture(
                buildErrorResult(chunkId, "AI analysis failed: " + e.getMessage()));
        }
    }

    /**
     * 解析 AI 返回的 JSON 响应为 ChunkReviewResult
     */
    ChunkReviewResult parseResponse(String json, String chunkId) {
        ChunkReviewResult.ChunkReviewResultBuilder builder = ChunkReviewResult.builder()
            .chunkId(chunkId);

        try {
            JsonNode root = objectMapper.readTree(json);

            // summary
            JsonNode summaryNode = root.get("summary");
            builder.summary(summaryNode != null ? summaryNode.asText() : "");

            // riskLevel
            JsonNode riskLevelNode = root.get("riskLevel");
            if (riskLevelNode != null) {
                try {
                    builder.riskLevel(ChunkReviewResult.RiskLevel.valueOf(riskLevelNode.asText().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    builder.riskLevel(ChunkReviewResult.RiskLevel.LOW);
                }
            }

            // risks
            JsonNode risksNode = root.get("risks");
            if (risksNode != null && risksNode.isArray()) {
                List<ChunkReviewResult.RiskItem> risks = new ArrayList<>();
                for (JsonNode riskNode : risksNode) {
                    risks.add(new ChunkReviewResult.RiskItem(
                        getTextOrDefault(riskNode, "type", "Unknown"),
                        riskNode.has("line") ? riskNode.get("line").asInt() : 0,
                        getTextOrDefault(riskNode, "description", "")
                    ));
                }
                builder.risks(risks);
            }

            // suggestions
            JsonNode suggestionsNode = root.get("suggestions");
            if (suggestionsNode != null && suggestionsNode.isArray()) {
                List<ChunkReviewResult.SuggestionItem> suggestions = new ArrayList<>();
                for (JsonNode suggestionNode : suggestionsNode) {
                    suggestions.add(new ChunkReviewResult.SuggestionItem(
                        suggestionNode.has("priority") ? suggestionNode.get("priority").asInt() : 5,
                        getTextOrDefault(suggestionNode, "description", "")
                    ));
                }
                builder.suggestions(suggestions);
            }

            // crossChunkHints (optional, only for chunk mode)
            JsonNode hintsNode = root.get("crossChunkHints");
            if (hintsNode != null) {
                builder.crossChunkHints(hintsNode.asText());
            }

        } catch (JsonMappingException e) {
            log.warn("Failed to parse AI response JSON for chunk {}: {}", chunkId, e.getMessage());
            return buildErrorResult(chunkId, "Failed to parse AI response: " + e.getMessage());
        } catch (JsonProcessingException e) {
            log.warn("Invalid JSON from AI for chunk {}: {}", chunkId, e.getMessage());
            return buildErrorResult(chunkId, "Invalid JSON from AI: " + e.getMessage());
        }

        return builder.build();
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null ? fieldNode.asText() : defaultValue;
    }

    private ChunkReviewResult buildErrorResult(String chunkId, String errorMessage) {
        return ChunkReviewResult.builder()
            .chunkId(chunkId)
            .summary(errorMessage)
            .riskLevel(ChunkReviewResult.RiskLevel.LOW)
            .risks(List.of(new ChunkReviewResult.RiskItem("Error", 0, errorMessage)))
            .build();
    }
}
