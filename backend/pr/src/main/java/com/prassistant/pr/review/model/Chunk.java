package com.prassistant.pr.review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单块单元 — 分块分析中的最小处理单元
 *
 * <p>每个 Chunk 是自包含的上下文包，携带代码片段、全局锚点和前序摘要，
 * 允许单块独立发送给 AI 分析，无需 API 端维护对话记忆。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {

    /** 块唯一标识（如 "UserService.java#hunk-2"） */
    private String chunkId;

    /** 源文件路径 */
    private String filePath;

    /** 去噪后的代码片段（该块实际内容） */
    private String content;

    /** 全局锚点（类名、方法名、当前块序号/总块数） */
    private String globalAnchor;

    /** 前序摘要（前序块的关键变更，用于跨块依赖传递） */
    private String previousSummary;

    /** 该块预计算的 Token 数 */
    private int tokenCount;

    /** 行号范围（旧文件起始~新文件结束，如 "-10,12 +10,15"） */
    private String hunkRange;

    /** 是否需要进一步拆分（单个 Hunk 仍超长时标记） */
    private boolean needsFurtherSplit;
}
