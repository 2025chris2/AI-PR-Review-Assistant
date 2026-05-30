package com.prassistant.pr.aggregation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PR 元数据 — 描述本次 Pull Request 的基本信息
 *
 * <p>从 GitHub PR 信息中提取，供第三层全局聚合使用。
 * 用于为 AI 提供 PR 级别的背景信息（变更范围、影响等）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrMetadata {

    /** PR 标题 */
    private String title;

    /** PR 描述（开发者写的变更说明） */
    private String description;

    /** 提交者 */
    private String author;

    /** 目标分支 */
    private String baseBranch;

    /** 源分支 */
    private String headBranch;

    /** PR 地址 */
    private String prUrl;

    /** 文件总数 */
    private int totalFiles;

    /** 新增行数 */
    private int totalAdditions;

    /** 删除行数 */
    private int totalDeletions;

    /** 变更文件类型分布，如 ["Java: 8", "XML: 2"] */
    private List<String> changedFileTypes;
}
