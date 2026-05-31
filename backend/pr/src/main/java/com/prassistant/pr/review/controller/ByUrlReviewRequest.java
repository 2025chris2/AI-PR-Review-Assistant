package com.prassistant.pr.review.controller;

/**
 * 粘贴 GitHub PR URL 发起 Review 的请求
 *
 * @param url   GitHub PR URL，格式如 https://github.com/owner/repo/pull/123
 * @param token GitHub Personal Access Token（可选，公开仓库不需要）
 */
public record ByUrlReviewRequest(String url, String token) {
}
