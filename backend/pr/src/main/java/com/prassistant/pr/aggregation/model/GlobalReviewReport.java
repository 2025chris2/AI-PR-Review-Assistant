package com.prassistant.pr.aggregation.model;

import com.prassistant.pr.review.model.FileReviewReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 第三层最终产出 — 全局 Review 报告
 *
 * <p>这是整个后端向前端交付的唯一数据结构。包含 PR 级别的变更总结、全局风险评级、
 * 跨文件关联问题、架构建议以及各文件详细报告。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalReviewReport {

    /** 分析任务 ID（用于 SSE 关联） */
    private String taskId;

    /** 原始 PR 地址 */
    private String prUrl;

    /** PR 整体变更总结（80 字内） */
    private String overallSummary;

    /** 全局风险评级 */
    private RiskLevel globalRiskLevel;

    /** 全局风险评级理由 */
    private String globalRiskReason;

    /** 跨文件关联问题列表 */
    private List<CrossFileIssue> crossFileIssues;

    /** 架构/设计建议 */
    private List<String> architectureSuggestions;

    /** 最需要优先 Review 的文件路径列表 */
    private List<String> topPriorityFiles;

    /** 各文件完整报告（透传第二层） */
    private List<FileReviewReport> fileReports;

    /** 总耗时（ms） */
    private long analysisTimeMs;

    /** 错误信息（聚合失败时填充，正常为 null） */
    private String error;

    /** 是否分析成功 */
    public boolean isSuccess() {
        return error == null;
    }

    /** 风险评级枚举 */
    public enum RiskLevel {
        HIGH, MEDIUM, LOW
    }

    /** 跨文件关联问题类型枚举 */
    public enum IssueType {
        INTERFACE_MISMATCH,
        DUPLICATE_LOGIC,
        TRANSACTION_MISSING,
        SECURITY_PROPAGATION,
        DB_CODE_INCONSISTENCY,
        OTHER
    }

    /**
     * 跨文件关联问题
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrossFileIssue {

        /** 问题类型 */
        private IssueType issueType;

        /** 问题描述 */
        private String description;

        /** 涉及文件路径列表 */
        private List<String> involvedFiles;

        /** 严重级别 */
        private String severity;

        /** 修复建议 */
        private String suggestion;
    }
}
