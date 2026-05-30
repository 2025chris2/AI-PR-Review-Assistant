package com.prassistant.pr.review.splitter;

import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.review.model.Chunk;

import java.util.List;

/**
 * 分块策略接口 — Strategy 模式
 *
 * <p>将 {@link SanitizedDiff} 切分为 {@link Chunk} 列表，
 * 供 {@link com.prassistant.pr.review.FileChunkAnalyzer} 选择合适策略执行分块。</p>
 *
 * <h3>已有实现</h3>
 * <ul>
 *   <li>{@link HunkBasedChunkSplitter} — 首选：按 Hunk 边界切分</li>
 *   <li>{@link FunctionBasedChunkSplitter} — 兜底：对仍超长 Hunk 按函数边界二次切分</li>
 * </ul>
 */
public interface ChunkSplitter {

    /**
     * 将去噪后的文件 diff 切分为多个 Chunk
     *
     * @param diff 第一层产出去噪结果
     * @return 切分后的 Chunk 列表（按代码顺序排列）
     */
    List<Chunk> split(SanitizedDiff diff);
}
