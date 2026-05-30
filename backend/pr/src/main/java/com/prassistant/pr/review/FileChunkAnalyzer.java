package com.prassistant.pr.review;

import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.review.analyzer.ChunkAnalyzer;
import com.prassistant.pr.review.analyzer.ChunkPromptBuilder;
import com.prassistant.pr.review.analyzer.FileReportReducer;
import com.prassistant.pr.review.model.Chunk;
import com.prassistant.pr.review.model.ChunkReviewResult;
import com.prassistant.pr.review.model.FileReviewReport;
import com.prassistant.pr.review.splitter.FunctionBasedChunkSplitter;
import com.prassistant.pr.review.splitter.HunkBasedChunkSplitter;
import com.prassistant.pr.review.util.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 第二层门面 — 文件分块分析入口
 *
 * <p>接收第一层产出的 {@link SanitizedDiff}，内部自动决策：</p>
 * <ul>
 *   <li><b>整文件分析</b>：Token ≤ 8000，一次 AI 调用</li>
 *   <li><b>分块分析</b>：Token > 8000，Hunk 切分 → 必要时函数二次切分 → 并行 Map → Reduce 聚合</li>
 * </ul>
 *
 * <p>对外暴露统一的 {@link #analyze(SanitizedDiff)} 和 {@link #analyzeAll(List)} 方法。</p>
 */
@Service
public class FileChunkAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(FileChunkAnalyzer.class);

    /** 分块阈值 */
    static final int CHUNK_THRESHOLD = TokenEstimator.DEFAULT_CHUNK_THRESHOLD;

    private final HunkBasedChunkSplitter hunkSplitter;
    private final FunctionBasedChunkSplitter functionSplitter;
    private final ChunkAnalyzer chunkAnalyzer;
    private final FileReportReducer reportReducer;
    private final Executor executor;

    public FileChunkAnalyzer(HunkBasedChunkSplitter hunkSplitter,
                             FunctionBasedChunkSplitter functionSplitter,
                             ChunkAnalyzer chunkAnalyzer,
                             FileReportReducer reportReducer) {
        this.hunkSplitter = hunkSplitter;
        this.functionSplitter = functionSplitter;
        this.chunkAnalyzer = chunkAnalyzer;
        this.reportReducer = reportReducer;
        // 为每个文件分配独立线程，避免 @Async 线程池竞争
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 分析单个文件：自动选择整文件或分块路径
     *
     * @param diff 第一层去噪后的文件 diff
     * @return 文件级 Review 报告
     */
    public CompletableFuture<FileReviewReport> analyze(SanitizedDiff diff) {
        return CompletableFuture.supplyAsync(() -> {
            String filePath = diff.getFilePath() != null ? diff.getFilePath() : "unknown";
            log.info("Analyzing file: {} ({} hunks)", filePath, diff.getHunks() != null ? diff.getHunks().size() : 0);

            try {
                String content = diff.getSanitizedContent();
                if (content == null || content.isBlank()) {
                    log.warn("Empty sanitized content for file: {}", filePath);
                    return buildEmptyReport(diff);
                }

                int tokenCount = TokenEstimator.count(content);
                log.debug("File {} token count: {} (threshold: {})", filePath, tokenCount, CHUNK_THRESHOLD);

                if (tokenCount <= CHUNK_THRESHOLD) {
                    return analyzeWholeFile(diff);
                } else {
                    return analyzeChunked(diff);
                }
            } catch (Exception e) {
                log.error("Failed to analyze file {}: {}", filePath, e.getMessage());
                return FileReviewReport.builder()
                    .filePath(filePath)
                    .status(diff.getStatus())
                    .overallSummary("分析失败: " + e.getMessage())
                    .riskLevel(FileReviewReport.RiskLevel.LOW)
                    .error(e.getMessage())
                    .build();
            }
        }, executor);
    }

    /**
     * 批量分析多个文件（并行）
     *
     * @param diffs 第一层去噪后的文件 diff 列表
     * @return 文件级 Review 报告列表
     */
    public CompletableFuture<List<FileReviewReport>> analyzeAll(List<SanitizedDiff> diffs) {
        if (diffs == null || diffs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<CompletableFuture<FileReviewReport>> futures = diffs.stream()
            .map(this::analyze)
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }

    // ==================== 内部方法 ====================

    /**
     * 路径 A：整文件分析
     */
    private FileReviewReport analyzeWholeFile(SanitizedDiff diff) {
        String filePath = diff.getFilePath() != null ? diff.getFilePath() : "unknown";
        String prompt = ChunkPromptBuilder.buildWholeFile(diff);
        String chunkId = filePath + "#whole";

        ChunkReviewResult result = chunkAnalyzer.analyze(prompt, chunkId).join();

        return reportReducer.reduce(List.of(result), filePath, diff.getStatus());
    }

    /**
     * 路径 B：分块分析
     */
    private FileReviewReport analyzeChunked(SanitizedDiff diff) {
        String filePath = diff.getFilePath() != null ? diff.getFilePath() : "unknown";

        // 1. 按 Hunk 切分
        List<Chunk> initialChunks = hunkSplitter.split(diff);

        // 2. 对超长块按函数二次拆分
        List<Chunk> finalChunks = new ArrayList<>();
        for (Chunk chunk : initialChunks) {
            if (chunk.isNeedsFurtherSplit()) {
                log.debug("Chunk {} exceeds threshold, applying function-based split", chunk.getChunkId());
                finalChunks.addAll(functionSplitter.refine(chunk));
            } else {
                finalChunks.add(chunk);
            }
        }
        log.info("File {}: split into {} chunks (initial: {})", filePath, finalChunks.size(), initialChunks.size());

        // 3. Map 阶段：并行分析各 Chunk
        List<CompletableFuture<ChunkReviewResult>> futures = finalChunks.stream()
            .map(chunk -> {
                String prompt = ChunkPromptBuilder.buildChunk(chunk);
                return chunkAnalyzer.analyze(prompt, chunk.getChunkId());
            })
            .collect(Collectors.toList());

        List<ChunkReviewResult> chunkResults = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());

        // 4. Reduce 阶段：聚合
        return reportReducer.reduce(chunkResults, filePath, diff.getStatus());
    }

    private FileReviewReport buildEmptyReport(SanitizedDiff diff) {
        return FileReviewReport.builder()
            .filePath(diff.getFilePath() != null ? diff.getFilePath() : "unknown")
            .status(diff.getStatus())
            .overallSummary("无变更内容")
            .riskLevel(FileReviewReport.RiskLevel.LOW)
            .analysisMethod(FileReviewReport.AnalysisMethod.WHOLE_FILE)
            .build();
    }
}
