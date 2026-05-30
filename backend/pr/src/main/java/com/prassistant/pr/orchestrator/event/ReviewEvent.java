package com.prassistant.pr.orchestrator.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Review 分析事件 — 用于 SSE 推送的消息体
 *
 * <p>包含事件类型、任务 ID、关联文件路径、消息和时间戳等信息。
 * {@code data} 字段可携带额外负载。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewEvent {

    /** 事件类型 */
    private ReviewEventType type;

    /** 任务 ID */
    private String taskId;

    /** 关联文件路径（文件级别事件时填充） */
    private String filePath;

    /** 事件描述消息 */
    private String message;

    /** 事件时间戳 */
    private Instant timestamp;

    /** 额外数据（各阶段可附加自定义负载） */
    private Map<String, Object> data;

    /**
     * 快速创建文件级事件
     *
     * @param type    事件类型（L2_FILE_START / L2_FILE_COMPLETE）
     * @param taskId  任务 ID
     * @param filePath 文件路径
     * @param message 描述消息
     * @return ReviewEvent
     */
    public static ReviewEvent fileEvent(ReviewEventType type, String taskId,
                                         String filePath, String message) {
        return ReviewEvent.builder()
                .type(type)
                .taskId(taskId)
                .filePath(filePath)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 快速创建阶段事件
     *
     * @param type    事件类型
     * @param taskId  任务 ID
     * @param message 描述消息
     * @return ReviewEvent
     */
    public static ReviewEvent stageEvent(ReviewEventType type, String taskId, String message) {
        return ReviewEvent.builder()
                .type(type)
                .taskId(taskId)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
