package com.prassistant.pr.review.controller;

import java.util.List;

/**
 * 发起 Review 请求
 *
 * @param prUrl       PR 地址
 * @param rawDiff     原始 Git Diff 文本
 * @param title       PR 标题
 * @param description PR 描述
 * @param author      提交者
 * @param baseBranch  目标分支
 * @param headBranch  源分支
 * @param totalFiles  变更文件数
 * @param totalAdditions 新增行数
 * @param totalDeletions 删除行数
 * @param changedFileTypes 文件类型分布
 */
public record ReviewRequest(
        String prUrl,
        String rawDiff,
        String title,
        String description,
        String author,
        String baseBranch,
        String headBranch,
        int totalFiles,
        int totalAdditions,
        int totalDeletions,
        List<String> changedFileTypes
) {}
