package com.prassistant.pr.aggregation;

import com.prassistant.pr.aggregation.model.PrMetadata;
import com.prassistant.pr.review.model.FileReviewReport;

import java.util.List;

/**
 * 全局提示词组装器 — 将各文件报告、PR 元数据和跨文件线索组装为 AI 全局分析提示词
 *
 * <p>输入已经是高度压缩的文本摘要（而非原始代码），Token 压力极小。
 * 单次 AI 调用即可完成整个 PR 的全局推理。</p>
 */
public final class GlobalPromptBuilder {

    /** 系统角色设定 */
    static final String SYSTEM_ROLE = "你是一位资深的代码评审专家。请对以下 Pull Request 进行全局评审分析。";

    /** 全局输出格式要求 */
    static final String OUTPUT_FORMAT = """
        请严格按以下 JSON 格式输出（不要包含 Markdown 代码块标记）：
        {
          "overallSummary": "PR 整体变更总结（80字内）",
          "globalRiskLevel": "HIGH|MEDIUM|LOW",
          "globalRiskReason": "全局风险评级理由",
          "crossFileIssues": [
            {
              "issueType": "INTERFACE_MISMATCH|DUPLICATE_LOGIC|TRANSACTION_MISSING|SECURITY_PROPAGATION|DB_CODE_INCONSISTENCY|OTHER",
              "description": "问题描述",
              "involvedFiles": ["文件路径1", "文件路径2"],
              "severity": "HIGH|MEDIUM|LOW",
              "suggestion": "修复建议"
            }
          ],
          "architectureSuggestions": ["建议1", "建议2"],
          "topPriorityFiles": ["最需要优先Review的文件路径"]
        }
        """;

    private GlobalPromptBuilder() {
        // 工具类，禁止实例化
    }

    /**
     * 构建全局分析 Prompt
     *
     * @param fileReports   第二层各文件 Review 报告（已压缩）
     * @param metadata      PR 元数据
     * @param crossFileHints CrossFileAnalyzer 预提取的跨文件线索
     * @return 发给 AI 的完整提示词
     */
    public static String build(List<FileReviewReport> fileReports,
                                PrMetadata metadata,
                                List<String> crossFileHints) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_ROLE).append("\n\n");

        // === PR 元数据 ===
        appendPrMetadata(sb, metadata);

        // === 各文件分析摘要 ===
        appendFileSummaries(sb, fileReports);

        // === 跨文件关联线索 ===
        appendCrossFileHints(sb, crossFileHints);

        // === 输出格式要求 ===
        sb.append("\n【输出格式】\n");
        sb.append(OUTPUT_FORMAT);

        return sb.toString();
    }

    private static void appendPrMetadata(StringBuilder sb, PrMetadata metadata) {
        sb.append("【PR 元数据】\n");
        if (metadata != null) {
            sb.append("标题：").append(nullToEmpty(metadata.getTitle())).append("\n");
            sb.append("描述：").append(nullToEmpty(metadata.getDescription())).append("\n");
            sb.append("作者：").append(nullToEmpty(metadata.getAuthor())).append("\n");
            sb.append("分支：").append(nullToEmpty(metadata.getHeadBranch()))
                .append(" → ").append(nullToEmpty(metadata.getBaseBranch())).append("\n");
            sb.append("文件数：").append(metadata.getTotalFiles())
                .append("，新增：+").append(metadata.getTotalAdditions())
                .append("，删除：-").append(metadata.getTotalDeletions()).append("\n");
            if (metadata.getChangedFileTypes() != null && !metadata.getChangedFileTypes().isEmpty()) {
                sb.append("文件类型分布：").append(String.join(", ", metadata.getChangedFileTypes())).append("\n");
            }
        }
        sb.append("\n");
    }

    private static void appendFileSummaries(StringBuilder sb, List<FileReviewReport> fileReports) {
        sb.append("【各文件分析摘要】\n");
        if (fileReports == null || fileReports.isEmpty()) {
            sb.append("（无变更文件）\n\n");
            return;
        }

        for (int i = 0; i < fileReports.size(); i++) {
            FileReviewReport report = fileReports.get(i);
            String path = report.getFilePath() != null ? report.getFilePath() : "unknown";
            String status = report.getStatus() != null ? report.getStatus().getValue() : "unknown";
            String risk = report.getRiskLevel() != null ? report.getRiskLevel().name() : "LOW";

            sb.append(i + 1).append(". ").append(path)
                .append(" [").append(status).append("]")
                .append(" risk=").append(risk).append("\n");

            String summary = report.getOverallSummary();
            if (summary != null && !summary.isBlank()) {
                sb.append("   摘要：").append(summary).append("\n");
            }

            // 风险点摘要
            if (report.getRisks() != null && !report.getRisks().isEmpty()) {
                sb.append("   风险点：");
                boolean first = true;
                for (var riskItem : report.getRisks()) {
                    if (!first) sb.append("；");
                    sb.append("[").append(riskItem.type()).append("]");
                    if (riskItem.line() > 0) {
                        sb.append("(行").append(riskItem.line()).append(")");
                    }
                    sb.append(" ").append(riskItem.description());
                    first = false;
                }
                sb.append("\n");
            }

            // 建议摘要
            if (report.getSuggestions() != null && !report.getSuggestions().isEmpty()) {
                sb.append("   建议：");
                boolean first = true;
                for (var suggestion : report.getSuggestions()) {
                    if (!first) sb.append("；");
                    sb.append("[").append(suggestion.priority()).append("] ")
                        .append(suggestion.description());
                    first = false;
                }
                sb.append("\n");
            }

            sb.append("\n");
        }
    }

    private static void appendCrossFileHints(StringBuilder sb, List<String> crossFileHints) {
        sb.append("【跨文件关联线索】\n");
        if (crossFileHints == null || crossFileHints.isEmpty()) {
            sb.append("（规则引擎未发现明显的跨文件关联）\n\n");
            return;
        }

        for (String hint : crossFileHints) {
            sb.append("- ").append(hint).append("\n");
        }
        sb.append("\n");
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
