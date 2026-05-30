package com.prassistant.pr.review.splitter;

import com.prassistant.pr.diff.model.DiffHunk;
import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.review.model.Chunk;
import com.prassistant.pr.review.util.TokenEstimator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 首选分块策略 — 按 Hunk 边界切分
 *
 * <p>将 {@link SanitizedDiff} 中的每个 {@link DiffHunk} 映射为一个 {@link Chunk}，
 * 并在块间传递前序摘要（{@link Chunk#previousSummary}）以保持上下文连贯性。</p>
 *
 * <p>若单个 Hunk 的 Token 数超过默认阈值（8000），
 * 标记 {@link Chunk#needsFurtherSplit} 为 true，交由下游 {@link FunctionBasedChunkSplitter} 二次拆分。</p>
 */
@Component
public class HunkBasedChunkSplitter implements ChunkSplitter {

    /** 分块触发阈值 */
    static final int CHUNK_THRESHOLD = TokenEstimator.DEFAULT_CHUNK_THRESHOLD;

    @Override
    public List<Chunk> split(SanitizedDiff diff) {
        List<Chunk> chunks = new ArrayList<>();
        List<DiffHunk> hunks = diff.getHunks();

        if (hunks == null || hunks.isEmpty()) {
            return chunks;
        }

        String previousSummary = null;

        for (int i = 0; i < hunks.size(); i++) {
            DiffHunk hunk = hunks.get(i);
            Chunk chunk = buildChunk(diff, hunk, i, hunks.size(), previousSummary);
            chunks.add(chunk);
            previousSummary = buildPreviousSummary(chunk);
        }

        return chunks;
    }

    /**
     * 将单个 DiffHunk 构建为 Chunk
     */
    private Chunk buildChunk(SanitizedDiff diff, DiffHunk hunk, int index, int total, String previousSummary) {
        String content = buildContent(hunk);
        int tokenCount = TokenEstimator.count(content);

        return Chunk.builder()
            .chunkId(diff.getFilePath() + "#hunk-" + (index + 1))
            .filePath(diff.getFilePath())
            .content(content)
            .globalAnchor(buildGlobalAnchor(diff, hunk, index, total))
            .previousSummary(previousSummary)
            .tokenCount(tokenCount)
            .hunkRange(formatHunkRange(hunk))
            .needsFurtherSplit(tokenCount > CHUNK_THRESHOLD)
            .build();
    }

    /**
     * 从 DiffHunk 拼接代码内容（@@ 头 + 所有行）
     */
    private String buildContent(DiffHunk hunk) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("@@ -%d,%d +%d,%d @@ %s%n",
            hunk.getOldStartLine(), hunk.getOldLineCount(),
            hunk.getNewStartLine(), hunk.getNewLineCount(),
            hunk.getSectionHeader() != null ? hunk.getSectionHeader() : ""));
        for (String line : hunk.getLines()) {
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 构建全局锚点字符串
     */
    private String buildGlobalAnchor(SanitizedDiff diff, DiffHunk hunk, int index, int total) {
        String section = hunk.getSectionHeader() != null && !hunk.getSectionHeader().isEmpty()
            ? hunk.getSectionHeader()
            : "unknown";
        return String.format("文件: %s | 类/函数: %s | 第 %d/%d 块",
            diff.getFilePath(), section, index + 1, total);
    }

    /**
     * 格式化行号范围
     */
    private String formatHunkRange(DiffHunk hunk) {
        return String.format("-%d,%d +%d,%d",
            hunk.getOldStartLine(), hunk.getOldLineCount(),
            hunk.getNewStartLine(), hunk.getNewLineCount());
    }

    /**
     * 从 Chunk 提取前序摘要（当前 chunk 的内容摘要，供下一个 chunk 使用）
     *
     * <p>由于此时尚未经过 AI 分析，此处使用简单的行类型统计作为过渡摘要，
     * 等 Map 阶段 {@link com.prassistant.pr.review.analyzer.ChunkAnalyzer} 完成后，
     * 会用真实的 {@code crossChunkHints} 在 Reduce 阶段更新。</p>
     */
    private String buildPreviousSummary(Chunk chunk) {
        String content = chunk.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }
        String[] lines = content.split("\n");
        int added = 0, removed = 0, context = 0;
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("++")) added++;
            else if (line.startsWith("-") && !line.startsWith("--")) removed++;
            else if (line.startsWith(" ")) context++;
        }
        return String.format("区块 %s: +%d/-%d 行变更, 共 %d 行上下文",
            chunk.getChunkId(), added, removed, context);
    }
}
