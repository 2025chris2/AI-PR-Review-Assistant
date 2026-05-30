package com.prassistant.pr.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 分析任务上下文 — 承载单个 PR 分析请求的完整生命周期
 *
 * <p>包含任务标识、当前状态、时间戳、错误收集等元信息。
 * Orchestrator 通过此对象追踪每个阶段的执行状态。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTask {

    /** 分析任务 ID（8 位 UUID 前缀） */
    private String taskId;

    /** PR 地址 */
    private String prUrl;

    /** 任务当前状态 */
    private ReviewState state;

    /** 任务创建时间 */
    private Instant createdAt;

    /** 任务最近更新时间 */
    private Instant updatedAt;

    /** 非致命错误列表（单个文件分析失败等，不阻塞整体） */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /** 致命错误信息（导致任务整体失败） */
    private String error;

    // ===== 便捷方法 =====

    /** 是否为终态 */
    public boolean isTerminal() {
        return state != null && state.isTerminal();
    }

    /** 是否成功完成 */
    public boolean isCompleted() {
        return state == ReviewState.COMPLETED;
    }

    /** 是否失败 */
    public boolean isFailed() {
        return state == ReviewState.FAILED;
    }

    /** 添加非致命错误 */
    public void addError(String errorMsg) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(errorMsg);
    }

    /** 标记任务失败 */
    public void fail(String errorMsg) {
        this.state = ReviewState.FAILED;
        this.error = errorMsg;
        this.updatedAt = Instant.now();
    }

    /** 切换到下一状态 */
    public void transitionTo(ReviewState newState) {
        this.state = newState;
        this.updatedAt = Instant.now();
    }
}
