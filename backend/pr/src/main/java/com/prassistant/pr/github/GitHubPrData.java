package com.prassistant.pr.github;

import com.prassistant.pr.aggregation.model.PrMetadata;

/**
 * GitHub PR 完整数据 — 合并 PR 元数据和原始 diff
 *
 * <p>供 {@link GitHubApiClient#fetchAll} 一次性返回，
 * 调用方可直接传给 {@code ReviewOrchestrator.review(rawDiff, metadata)}。</p>
 *
 * @param metadata PR 元数据
 * @param rawDiff  原始 Git Diff 文本（multi-file unified diff）
 */
public record GitHubPrData(PrMetadata metadata, String rawDiff) {
}
