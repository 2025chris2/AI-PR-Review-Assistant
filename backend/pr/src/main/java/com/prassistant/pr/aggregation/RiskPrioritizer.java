package com.prassistant.pr.aggregation;

import com.prassistant.pr.aggregation.model.GlobalReviewReport;
import com.prassistant.pr.review.model.FileReviewReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 风险排序与后处理器 — 对 AI 返回的全局结果做二次校验和标准化
 *
 * <p>职责：</p>
 * <ul>
 *   <li>去重合并：AI 可能重复输出同一个跨文件问题</li>
 *   <li>风险定级校准：核心流程文件被标记 HIGH 但全局评级 LOW → 升级</li>
 *   <li>文件排序：按风险 × 影响面排序，填充 topPriorityFiles</li>
 *   <li>截断保护：确保 overallSummary 不超过 80 字</li>
 * </ul>
 */
public final class RiskPrioritizer {

    /** 核心流程关键词（支付、订单、库存等） */
    private static final Set<String> CORE_FLOW_KEYWORDS = Set.of(
            "pay", "payment", "order", "inventory", "stock", "transaction",
            "支付", "订单", "库存", "交易", "扣减", "退款"
    );

    private RiskPrioritizer() {
        // utility class
    }

    /**
     * 对 AI 返回的全局报告做后处理与标准化
     *
     * @param rawReport  AI 返回的原始报告
     * @return 处理后的最终报告
     */
    public static GlobalReviewReport prioritize(GlobalReviewReport rawReport) {
        if (rawReport == null) {
            return null;
        }

        // 1. 去重合并跨文件问题
        List<GlobalReviewReport.CrossFileIssue> dedupedIssues = dedupCrossFileIssues(
                rawReport.getCrossFileIssues());

        // 2. 风险校准
        GlobalReviewReport.RiskLevel calibratedRisk = calibrateRiskLevel(
                rawReport.getGlobalRiskLevel(),
                rawReport.getFileReports());

        // 3. 文件排序 + topPriorityFiles
        List<String> topFiles = computeTopPriorityFiles(rawReport.getFileReports());

        // 4. 截断保护
        String truncatedSummary = truncateSummary(rawReport.getOverallSummary());

        return GlobalReviewReport.builder()
                .taskId(rawReport.getTaskId())
                .prUrl(rawReport.getPrUrl())
                .overallSummary(truncatedSummary)
                .globalRiskLevel(calibratedRisk)
                .globalRiskReason(rawReport.getGlobalRiskReason())
                .crossFileIssues(dedupedIssues)
                .architectureSuggestions(rawReport.getArchitectureSuggestions())
                .topPriorityFiles(topFiles)
                .fileReports(sortFileReportsByRisk(rawReport.getFileReports()))
                .analysisTimeMs(rawReport.getAnalysisTimeMs())
                .error(rawReport.getError())
                .build();
    }

    /**
     * 去重合并跨文件问题：描述相似度超过阈值则合并
     */
    static List<GlobalReviewReport.CrossFileIssue> dedupCrossFileIssues(
            List<GlobalReviewReport.CrossFileIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return issues;
        }

        List<GlobalReviewReport.CrossFileIssue> result = new ArrayList<>();
        boolean[] merged = new boolean[issues.size()];

        for (int i = 0; i < issues.size(); i++) {
            if (merged[i]) continue;

            GlobalReviewReport.CrossFileIssue current = issues.get(i);
            Set<String> allFiles = new HashSet<>();
            if (current.getInvolvedFiles() != null) {
                allFiles.addAll(current.getInvolvedFiles());
            }

            // 检查后续是否有相似问题
            for (int j = i + 1; j < issues.size(); j++) {
                if (merged[j]) continue;

                String desc1 = current.getDescription() != null ? current.getDescription() : "";
                String desc2 = issues.get(j).getDescription() != null ? issues.get(j).getDescription() : "";

                if (wordOverlap(desc1, desc2) > 0.7) {
                    merged[j] = true;
                    if (issues.get(j).getInvolvedFiles() != null) {
                        allFiles.addAll(issues.get(j).getInvolvedFiles());
                    }
                }
            }

            result.add(GlobalReviewReport.CrossFileIssue.builder()
                    .issueType(current.getIssueType())
                    .description(current.getDescription())
                    .involvedFiles(List.copyOf(allFiles))
                    .severity(current.getSeverity())
                    .suggestion(current.getSuggestion())
                    .build());
        }

        return result;
    }

    /**
     * 风险定级校准：核心流程文件有 HIGH 但全局 LOW → 升级 MEDIUM
     */
    static GlobalReviewReport.RiskLevel calibrateRiskLevel(
            GlobalReviewReport.RiskLevel currentLevel,
            List<FileReviewReport> fileReports) {

        if (fileReports == null || fileReports.isEmpty()) {
            return currentLevel != null ? currentLevel : GlobalReviewReport.RiskLevel.LOW;
        }

        // 如果已经是 HIGH 或 MEDIUM，不需要升级
        if (currentLevel == GlobalReviewReport.RiskLevel.HIGH
                || currentLevel == GlobalReviewReport.RiskLevel.MEDIUM) {
            return currentLevel;
        }

        // 检查是否有核心流程文件被标记为 HIGH
        boolean hasCoreFlowHighRisk = fileReports.stream()
                .anyMatch(r -> r.getRiskLevel() == FileReviewReport.RiskLevel.HIGH
                        && isCoreFlowFile(r.getFilePath()));

        if (hasCoreFlowHighRisk) {
            return GlobalReviewReport.RiskLevel.MEDIUM;
        }

        return GlobalReviewReport.RiskLevel.LOW;
    }

    /**
     * 计算优先 Review 的文件列表（按风险排序的前 N 个）
     */
    static List<String> computeTopPriorityFiles(List<FileReviewReport> fileReports) {
        if (fileReports == null || fileReports.isEmpty()) {
            return List.of();
        }

        return fileReports.stream()
                .sorted(Comparator
                        .<FileReviewReport, Integer>comparing(r -> riskLevelScore(r.getRiskLevel()))
                        .reversed()
                        .thenComparing(r -> r.getFilePath() != null ? r.getFilePath() : ""))
                .limit(3)
                .map(r -> r.getFilePath() != null ? r.getFilePath() : "unknown")
                .collect(Collectors.toList());
    }

    /**
     * 按风险排序文件报告（HIGH → MEDIUM → LOW）
     */
    static List<FileReviewReport> sortFileReportsByRisk(List<FileReviewReport> fileReports) {
        if (fileReports == null) {
            return null;
        }

        return fileReports.stream()
                .sorted(Comparator
                        .<FileReviewReport, Integer>comparing(r -> riskLevelScore(r.getRiskLevel()))
                        .reversed())
                .collect(Collectors.toList());
    }

    /**
     * 截断摘要到 80 字以内
     */
    static String truncateSummary(String summary) {
        if (summary == null) {
            return "";
        }
        if (summary.length() <= 80) {
            return summary;
        }
        return summary.substring(0, 77) + "...";
    }

    // ==================== 内部工具方法 ====================

    /**
     * 计算两个字符串的词重叠率（Jaccard 相似度）
     */
    static double wordOverlap(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.isEmpty() && b.isEmpty()) return 1.0;

        Set<String> wordsA = tokenize(a);
        Set<String> wordsB = tokenize(b);

        if (wordsA.isEmpty() && wordsB.isEmpty()) return 1.0;
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);

        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);

        return (double) intersection.size() / union.size();
    }

    /**
     * 分词
     */
    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else {
                if (current.length() > 0) {
                    tokens.add(current.toString().toLowerCase());
                    current.setLength(0);
                }
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString().toLowerCase());
        }

        return tokens;
    }

    /**
     * 判断文件路径是否属于核心流程
     */
    static boolean isCoreFlowFile(String filePath) {
        if (filePath == null) return false;
        String lower = filePath.toLowerCase();
        return CORE_FLOW_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /**
     * 风险级别转数值（用于排序）
     */
    private static int riskLevelScore(FileReviewReport.RiskLevel level) {
        if (level == null) return 0;
        return switch (level) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }
}
