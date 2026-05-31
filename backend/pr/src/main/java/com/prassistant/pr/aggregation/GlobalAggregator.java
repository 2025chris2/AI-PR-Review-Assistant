package com.prassistant.pr.aggregation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prassistant.pr.aggregation.model.GlobalReviewReport;
import com.prassistant.pr.aggregation.model.PrMetadata;
import com.prassistant.pr.review.model.FileReviewReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 第三层门面 — 全局聚合入口
 *
 * <p>接收第二层全部 {@link FileReviewReport} 和 PR 元数据，串行执行全局聚合流程：</p>
 * <ol>
 *   <li>CrossFileAnalyzer 规则引擎提取跨文件线索</li>
 *   <li>GlobalPromptBuilder 组装 AI 提示词</li>
 *   <li>单次 AI 调用（无需分块）</li>
 *   <li>解析 AI JSON 响应为 GlobalReviewReport</li>
 *   <li>RiskPrioritizer 后处理与标准化</li>
 * </ol>
 */
@Service
public class GlobalAggregator {

    private static final Logger log = LoggerFactory.getLogger(GlobalAggregator.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public GlobalAggregator(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 全局聚合入口
     *
     * @param fileReports 第二层全部文件 Review 报告
     * @param metadata    PR 元数据
     * @return 全局 Review 报告
     */
    public GlobalReviewReport aggregate(List<FileReviewReport> fileReports, PrMetadata metadata) {
        Instant start = Instant.now();

        if (fileReports == null || fileReports.isEmpty()) {
            log.warn("aggregate() called with empty file reports");
            return buildErrorReport("无变更文件，跳过全局分析");
        }

        log.info("Starting L3 aggregation files={}", fileReports.size());

        try {
            // Step 1: 跨文件线索提取
            List<String> hints = CrossFileAnalyzer.extractHints(fileReports);
            log.debug("CrossFileAnalyzer extracted {} hints", hints.size());

            // Step 2: 组装提示词
            String prompt = GlobalPromptBuilder.build(fileReports, metadata, hints);
            log.debug("L3 prompt assembled ({} chars)", prompt.length());

            // Step 3: 单次 AI 调用
            log.info("Calling AI for global aggregation");
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                log.warn("AI returned empty response for global aggregation");
                return buildErrorReport("全局聚合 AI 返回空响应");
            }

            // Step 4: 解析 AI 响应（taskId 由调用方 ReviewOrchestrator 设置）
            GlobalReviewReport rawReport = parseResponse(response, fileReports);

            // Step 5: 后处理与标准化
            GlobalReviewReport finalReport = RiskPrioritizer.prioritize(rawReport);

            // Step 6: 补充元数据
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            finalReport.setAnalysisTimeMs(elapsed);

            log.info("L3 aggregation complete risk={}, time={}ms",
                    finalReport.getGlobalRiskLevel(), elapsed);
            return finalReport;

        } catch (Exception e) {
            log.error("Global aggregation failed: {}", e.getMessage());
            GlobalReviewReport errorReport = buildErrorReport("全局聚合失败: " + e.getMessage());
            errorReport.setAnalysisTimeMs(Duration.between(start, Instant.now()).toMillis());
            return errorReport;
        }
    }

    /**
     * 解析 AI 返回的 JSON 为 GlobalReviewReport
     */
    GlobalReviewReport parseResponse(String json, List<FileReviewReport> fileReports) {
        GlobalReviewReport.GlobalReviewReportBuilder builder = GlobalReviewReport.builder()
                .fileReports(fileReports);

        try {
            JsonNode root = objectMapper.readTree(json);

            // overallSummary
            JsonNode summaryNode = root.get("overallSummary");
            builder.overallSummary(summaryNode != null ? summaryNode.asText() : "");

            // globalRiskLevel
            JsonNode riskLevelNode = root.get("globalRiskLevel");
            if (riskLevelNode != null) {
                try {
                    builder.globalRiskLevel(
                            GlobalReviewReport.RiskLevel.valueOf(riskLevelNode.asText().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    builder.globalRiskLevel(GlobalReviewReport.RiskLevel.LOW);
                }
            }

            // globalRiskReason
            JsonNode reasonNode = root.get("globalRiskReason");
            builder.globalRiskReason(reasonNode != null ? reasonNode.asText() : "");

            // crossFileIssues
            JsonNode issuesNode = root.get("crossFileIssues");
            if (issuesNode != null && issuesNode.isArray()) {
                List<GlobalReviewReport.CrossFileIssue> issues = new ArrayList<>();
                for (JsonNode issueNode : issuesNode) {
                    GlobalReviewReport.CrossFileIssue.CrossFileIssueBuilder issueBuilder =
                            GlobalReviewReport.CrossFileIssue.builder();

                    // issueType（AI 输出 + 后端兜底分类）
                    JsonNode typeNode = issueNode.get("issueType");
                    GlobalReviewReport.IssueType resolvedType = GlobalReviewReport.IssueType.OTHER;
                    if (typeNode != null) {
                        try {
                            resolvedType = GlobalReviewReport.IssueType.valueOf(
                                    typeNode.asText().toUpperCase());
                        } catch (IllegalArgumentException e) {
                            // 忽略，由后端兜底
                        }
                    }
                    if (resolvedType == GlobalReviewReport.IssueType.OTHER) {
                        String desc = getTextOrDefault(issueNode, "description", "");
                        resolvedType = classifyIssueType(desc);
                    }
                    issueBuilder.issueType(resolvedType);

                    issueBuilder.description(getTextOrDefault(issueNode, "description", ""));
                    issueBuilder.severity(getTextOrDefault(issueNode, "severity", "MEDIUM"));
                    issueBuilder.suggestion(getTextOrDefault(issueNode, "suggestion", ""));

                    // involvedFiles
                    JsonNode filesNode = issueNode.get("involvedFiles");
                    if (filesNode != null && filesNode.isArray()) {
                        List<String> files = new ArrayList<>();
                        for (JsonNode fileNode : filesNode) {
                            files.add(fileNode.asText());
                        }
                        issueBuilder.involvedFiles(files);
                    }

                    issues.add(issueBuilder.build());
                }
                builder.crossFileIssues(issues);
            }

            // architectureSuggestions
            JsonNode archNode = root.get("architectureSuggestions");
            if (archNode != null && archNode.isArray()) {
                List<String> suggestions = new ArrayList<>();
                for (JsonNode sugNode : archNode) {
                    suggestions.add(sugNode.asText());
                }
                builder.architectureSuggestions(suggestions);
            }

            // topPriorityFiles
            JsonNode topFilesNode = root.get("topPriorityFiles");
            if (topFilesNode != null && topFilesNode.isArray()) {
                List<String> topFiles = new ArrayList<>();
                for (JsonNode fileNode : topFilesNode) {
                    topFiles.add(fileNode.asText());
                }
                builder.topPriorityFiles(topFiles);
            }

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse L3 AI response JSON: {}", e.getMessage());
            return buildErrorReport("全局聚合 AI 响应解析失败: " + e.getMessage());
        }

        return builder.build();
    }

    /**
     * 通过关键词匹配自动分类 issueType（兜底 AI 的分类失败）
     */
    static GlobalReviewReport.IssueType classifyIssueType(String description) {
        if (description == null || description.isBlank()) {
            return GlobalReviewReport.IssueType.OTHER;
        }
        String lower = description.toLowerCase();

        if (containsAny(lower, "精度", "浮点", "epsilon", "容差", "tolerance",
                "舍入", "condition number", "数值稳定性")) {
            return GlobalReviewReport.IssueType.NUMERICAL_ACCURACY;
        }
        if (containsAny(lower, "gram", "schmidt", "算法选型", "algorithm choice",
                "householder", "qr分解", "最小二乘", "orthogonal")) {
            return GlobalReviewReport.IssueType.ALGORITHM_CHOICE;
        }
        if (containsAny(lower, "性能", "performance", "拷贝", "分配", "内存",
                "开销", "overhead", "复杂度")) {
            return GlobalReviewReport.IssueType.PERFORMANCE;
        }
        if (containsAny(lower, "接口", "签名", "interface", "signature", "inconsistency")) {
            return GlobalReviewReport.IssueType.INTERFACE_INCONSISTENCY;
        }
        if (containsAny(lower, "重复", "冗余", "duplicate", "redundant")) {
            return GlobalReviewReport.IssueType.REPEAT_LOGIC;
        }
        if (containsAny(lower, "安全", "注入", "权限", "security", "injection", "vulnerability")) {
            return GlobalReviewReport.IssueType.SECURITY_VULNERABILITY;
        }
        if (containsAny(lower, "事务", "transaction", "原子性", "一致性")) {
            return GlobalReviewReport.IssueType.TRANSACTION_MISSING;
        }
        if (containsAny(lower, "mapper", "mybatis", "数据库", "字段", "column")) {
            return GlobalReviewReport.IssueType.DB_CODE_INCONSISTENCY;
        }
        return GlobalReviewReport.IssueType.OTHER;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null ? fieldNode.asText() : defaultValue;
    }

    private GlobalReviewReport buildErrorReport(String errorMessage) {
        return GlobalReviewReport.builder()
                .overallSummary("分析失败")
                .globalRiskLevel(GlobalReviewReport.RiskLevel.LOW)
                .globalRiskReason(errorMessage)
                .error(errorMessage)
                .build();
    }
}
