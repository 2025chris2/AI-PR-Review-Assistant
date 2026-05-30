package com.prassistant.pr.orchestrator.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Review 分析事件 — 用于 SSE 推送的消息体
 *
 * <p>包含事件类型、任务 ID、关联文件路径、消息和时间戳等信息。
 * {@code data} 字段可携带各阶段的结构化负载。</p>
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

    // ==================== 文件级事件 ====================

    /**
     * 快速创建文件级事件（不含额外数据）
     *
     * @param type     事件类型（L2_FILE_START / L2_FILE_CHUNK / L2_FILE_COMPLETE）
     * @param taskId   任务 ID
     * @param filePath 文件路径
     * @param message  描述消息
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
     * 快速创建文件级事件（含额外数据）
     *
     * @param type     事件类型
     * @param taskId   任务 ID
     * @param filePath 文件路径
     * @param message  描述消息
     * @param data     额外数据
     * @return ReviewEvent
     */
    public static ReviewEvent fileEvent(ReviewEventType type, String taskId,
                                         String filePath, String message,
                                         Map<String, Object> data) {
        return ReviewEvent.builder()
                .type(type)
                .taskId(taskId)
                .filePath(filePath)
                .message(message)
                .timestamp(Instant.now())
                .data(data)
                .build();
    }

    // ==================== 阶段级事件 ====================

    /**
     * 快速创建阶段事件（不含额外数据）
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

    /**
     * 快速创建阶段事件（含额外数据）
     *
     * @param type    事件类型
     * @param taskId  任务 ID
     * @param message 描述消息
     * @param data    额外数据
     * @return ReviewEvent
     */
    public static ReviewEvent stageEvent(ReviewEventType type, String taskId,
                                          String message, Map<String, Object> data) {
        return ReviewEvent.builder()
                .type(type)
                .taskId(taskId)
                .message(message)
                .timestamp(Instant.now())
                .data(data)
                .build();
    }

    // ==================== 错误事件 ====================

    /**
     * 创建错误事件
     *
     * <p>自动填充 data = {stage, message, recoverable}。</p>
     *
     * @param taskId     任务 ID
     * @param stage      出错阶段（如 "L1", "L2", "L3"）
     * @param message    错误描述
     * @param recoverable 是否可恢复（false 表示致命错误）
     * @return ReviewEvent
     */
    public static ReviewEvent errorEvent(String taskId, String stage,
                                          String message, boolean recoverable) {
        Map<String, Object> data = new HashMap<>();
        data.put("stage", stage);
        data.put("message", message);
        data.put("recoverable", recoverable);

        return ReviewEvent.builder()
                .type(recoverable ? ReviewEventType.ERROR : ReviewEventType.FAILED)
                .taskId(taskId)
                .message(message)
                .timestamp(Instant.now())
                .data(data)
                .build();
    }
}
