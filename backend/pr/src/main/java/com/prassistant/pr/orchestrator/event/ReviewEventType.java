package com.prassistant.pr.orchestrator.event;

/**
 * Review 分析事件类型 — 用于 SSE 向前端推送状态变更
 *
 * <p>每个枚举常量关联一个 SSE 事件名（{@link #getSseName()}），
 * 前后端通过此名称约定通信。</p>
 *
 * <p>事件流：TASK_STARTED → L1_COMPLETE → (L2_FILE_START → L2_FILE_CHUNK → L2_FILE_COMPLETE)*
 *         → L2_COMPLETE → L3_START → L3_COMPLETE → RESULT
 * <br>异常时任意阶段可触发 ERROR（可恢复）或 FAILED（不可恢复）</p>
 */
public enum ReviewEventType {

    /** 任务已创建（向后兼容，新代码请使用 TASK_STARTED） */
    TASK_CREATED("task.created"),

    /** 任务已开始调度 */
    TASK_STARTED("task.started"),

    /** L1 Diff 去噪完成 */
    L1_COMPLETE("l1.complete"),

    /** 开始分析单个文件 */
    L2_FILE_START("l2.file.start"),

    /** 单个文件的某个 Chunk 完成 */
    L2_FILE_CHUNK("l2.file.chunk"),

    /** 单个文件分析完成 */
    L2_FILE_COMPLETE("l2.file.complete"),

    /** L2 全部文件分析完成 */
    L2_COMPLETE("l2.complete"),

    /** 开始 L3 全局聚合 */
    L3_START("l3.start"),

    /** L3 全局聚合完成 */
    L3_COMPLETE("l3.complete"),

    /** 全流程完成，附带最终结果 */
    RESULT("result"),

    /** 全流程完成（向后兼容，新代码请使用 RESULT） */
    COMPLETED("completed"),

    /** 非致命错误（单个文件失败等，不影响整体） */
    ERROR("error"),

    /** 全流程失败（致命异常），与 ERROR 相同 SSE 事件名，由 recoverable 区分 */
    FAILED("error");

    private final String sseName;

    ReviewEventType(String sseName) {
        this.sseName = sseName;
    }

    /** 获取 SSE 事件名称（点号分隔格式，如 "l2.file.start"） */
    public String getSseName() {
        return sseName;
    }

    /** 是否与特定文件关联 */
    public boolean isFileEvent() {
        return this == L2_FILE_START
                || this == L2_FILE_CHUNK
                || this == L2_FILE_COMPLETE;
    }

    /** 是否表示流程结束 */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == RESULT;
    }
}
