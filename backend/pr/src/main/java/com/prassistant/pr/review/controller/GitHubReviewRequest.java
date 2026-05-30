package com.prassistant.pr.review.controller;

/**
 * 从 GitHub 拉起 PR 数据的 Review 请求
 *
 * <p>用户只需提供仓库信息和 GitHub Token，后端自动调用 GitHub API
 * 获取 PR 元数据和原始 diff。</p>
 *
 * @param owner    仓库所有者（用户或组织）
 * @param repo     仓库名称
 * @param prNumber PR 编号
 * @param token    GitHub Personal Access Token
 */
public record GitHubReviewRequest(
        String owner,
        String repo,
        int prNumber,
        String token
) {
}
