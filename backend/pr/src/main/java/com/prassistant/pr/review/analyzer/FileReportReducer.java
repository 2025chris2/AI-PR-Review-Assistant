package com.prassistant.pr.review.analyzer;

import com.prassistant.pr.diff.model.FileChangeType;
import com.prassistant.pr.review.model.ChunkReviewResult;
import com.prassistant.pr.review.model.FileReviewReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reduce 阶段执行器 — 将多个 Chunk 分析结果聚合成文件级报告
 *
 * <p>职责：</p>
 * <ul>
 *   <li>去重合并：多个 Chunk 提到同类风险时合并为一条</li>
 *   <li>风险升级：任一 Chunk 为 HIGH → 整文件 HIGH</li>
 *   <li>跨块一致性检查：扫描 crossChunkHints 发现跨块问题</li>
 *   <li>摘要精炼：拼接各块摘要生成文件级总结</li>
 * </ul>
 */
@Service
public class FileReportReducer {

    private static final Logger log = LoggerFactory.getLogger(FileReportReducer.class);

    /**
     * 将多个 Chunk 分析结果聚合成文件级报告
     *
     * @param chunkResults 各 Chunk 的 AI 分析结果（Map 阶段产出）
     * @param filePath  文件路径
     * @param status    文件变更状态
     * @return 文件级统一报告
     */
    public FileReviewReport reduce(List<ChunkReviewResult> chunkResults, String filePath, FileChangeType status) {
        if (chunkResults == null || chunkResults.isEmpty()) {
            log.warn("No chunk results to reduce for file: {}", filePath);
            return FileReviewReport.builder()
                .filePath(filePath)
                .status(status)
                .overallSummary("无分析结果")
                .riskLevel(FileReviewReport.RiskLevel.LOW)
                .analysisMethod(FileReviewReport.AnalysisMethod.WHOLE_FILE)
                .build();
        }

        // 1. 去重合并风险
        List<ChunkReviewResult.RiskItem> mergedRisks = mergeRisks(chunkResults);

        // 2. 合并排序建议
        List<ChunkReviewResult.SuggestionItem> sortedSuggestions = mergeSuggestions(chunkResults);

        // 3. 风险升级：任一 Chunk 为 HIGH → 整文件 HIGH
        FileReviewReport.RiskLevel fileRiskLevel = calculateRiskLevel(chunkResults);

        // 4. 跨块一致性检查
        List<String> crossChunkIssues = detectCrossChunkIssues(chunkResults);

        // 5. 文件级摘要
        String overallSummary = buildOverallSummary(chunkResults);

        // 6. 判断分析方法
        boolean isChunked = chunkResults.stream()
            .anyMatch(r -> r.getChunkId() != null && r.getChunkId().contains("-fn-"));
        FileReviewReport.AnalysisMethod method = isChunked || chunkResults.size() > 1
            ? FileReviewReport.AnalysisMethod.CHUNKED
            : FileReviewReport.AnalysisMethod.WHOLE_FILE;

        return FileReviewReport.builder()
            .filePath(filePath)
            .status(status)
            .overallSummary(overallSummary)
            .riskLevel(fileRiskLevel)
            .risks(mergedRisks)
            .suggestions(sortedSuggestions)
            .crossChunkIssues(crossChunkIssues)
            .analysisMethod(method)
            .build();
    }

    /**
     * 去重合并风险：同类风险（相同 type）合并，分不同 location 记录
     */
    List<ChunkReviewResult.RiskItem> mergeRisks(List<ChunkReviewResult> results) {
        Set<String> seen = new HashSet<>();
        List<ChunkReviewResult.RiskItem> merged = new ArrayList<>();
        for (ChunkReviewResult result : results) {
            if (result.getRisks() != null) {
                for (ChunkReviewResult.RiskItem risk : result.getRisks()) {
                    if (seen.add(risk.type())) {
                        merged.add(risk);
                    }
                }
            }
        }
        return merged;
    }

    /**
     * 合并排序建议：按优先级排序（priority 越小越靠前）
     */
    List<ChunkReviewResult.SuggestionItem> mergeSuggestions(List<ChunkReviewResult> results) {
        return results.stream()
            .filter(r -> r.getSuggestions() != null)
            .flatMap(r -> r.getSuggestions().stream())
            .sorted((a, b) -> Integer.compare(a.priority(), b.priority()))
            .collect(Collectors.toList());
    }

    /**
     * 计算文件级风险：任一 Chunk 为 HIGH → HIGH；任一 MEDIUM → MEDIUM；否则 LOW
     */
    FileReviewReport.RiskLevel calculateRiskLevel(List<ChunkReviewResult> results) {
        boolean hasHigh = false;
        boolean hasMedium = false;
        for (ChunkReviewResult result : results) {
            if (result.getRiskLevel() == ChunkReviewResult.RiskLevel.HIGH) {
                hasHigh = true;
            } else if (result.getRiskLevel() == ChunkReviewResult.RiskLevel.MEDIUM) {
                hasMedium = true;
            }
        }
        if (hasHigh) return FileReviewReport.RiskLevel.HIGH;
        if (hasMedium) return FileReviewReport.RiskLevel.MEDIUM;
        return FileReviewReport.RiskLevel.LOW;
    }

    /**
     * 跨块一致性检查：扫描所有 crossChunkHints，列出所有跨块依赖
     */
    List<String> detectCrossChunkIssues(List<ChunkReviewResult> results) {
        List<String> issues = new ArrayList<>();
        for (ChunkReviewResult result : results) {
            if (result.getCrossChunkHints() != null && !result.getCrossChunkHints().isBlank()) {
                issues.add("跨块依赖: [" + result.getChunkId() + "] " + result.getCrossChunkHints());
            }
        }
        return issues;
    }

    /**
     * 拼接各块摘要生成文件级总结
     *
     * <p>如果 AI 未使用模板格式，自动追加"变更 "前缀以保证格式统一。</p>
     */
    String buildOverallSummary(List<ChunkReviewResult> results) {
        String raw;
        if (results.size() == 1) {
            raw = results.get(0).getSummary();
        } else {
            raw = results.stream()
                .map(r -> r.getSummary() != null ? r.getSummary() : "")
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("; "));
        }
        if (raw == null || raw.isBlank()) return "";

        // AI 已使用模板格式则直接返回
        if (raw.contains("影响") || raw.contains("本质")) {
            return raw.length() > 80 ? raw.substring(0, 77) + "..." : raw;
        }

        // 否则加前缀
        String trimmed = raw.length() > 50 ? raw.substring(0, 47) + "..." : raw;
        return "变更 " + trimmed;
    }
}
