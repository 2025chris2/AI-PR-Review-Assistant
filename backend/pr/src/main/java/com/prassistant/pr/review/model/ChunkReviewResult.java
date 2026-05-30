package com.prassistant.pr.review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单块 AI 分析结果 — Map 阶段的产出
 *
 * <p>对应 {@link com.prassistant.pr.review.splitter.ChunkSplitter} 切分出的每个 Chunk，
 * 由 {@link com.prassistant.pr.review.analyzer.ChunkAnalyzer} 调用 AI 生成。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkReviewResult {

    /** 关联回 Chunk 的 ID */
    private String chunkId;

    /** 该块变更摘要（30 字内） */
    private String summary;

    /** 风险点列表 */
    private List<RiskItem> risks;

    /** Review 建议列表 */
    private List<SuggestionItem> suggestions;

    /** 跨块依赖提示（传给下一个 Chunk 的 previousSummary） */
    private String crossChunkHints;

    /** 该块风险评级 */
    private RiskLevel riskLevel;

    /** 风险评级枚举 */
    public enum RiskLevel {
        HIGH, MEDIUM, LOW
    }

    /** 单个风险点 */
    public record RiskItem(String type, int line, String description) {
    }

    /** 单个建议 */
    public record SuggestionItem(int priority, String description) {
    }
}
