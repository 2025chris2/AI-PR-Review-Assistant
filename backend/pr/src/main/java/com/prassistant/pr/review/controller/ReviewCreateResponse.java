package com.prassistant.pr.review.controller;

/**
 * 创建 Review 任务响应
 *
 * @param taskId    分析任务 ID
 * @param eventsUrl SSE 事件流地址
 * @param resultUrl 结果查询地址
 */
public record ReviewCreateResponse(
        String taskId,
        String eventsUrl,
        String resultUrl
) {}
