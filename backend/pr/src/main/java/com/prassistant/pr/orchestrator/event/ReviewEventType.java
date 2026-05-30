package com.prassistant.pr.orchestrator.event;

/**
 * Review 分析事件类型 — 用于 SSE 向前端推送状态变更
 *
 * <p>事件流：TASK_CREATED → L1_COMPLETE → (L2_FILE_START → L2_FILE_COMPLETE)* → L2_COMPLETE → L3_COMPLETE → COMPLETED
 * <br>异常时任意阶段可触发 ERROR 或 FAILED</p>
 */
public enum ReviewEventType {

    /** 任务已创建 */
    TASK_CREATED,

    /** L1 Diff 去噪完成 */
    L1_COMPLETE,

    /** 开始分析单个文件 */
    L2_FILE_START,

    /** 单个文件分析完成 */
    L2_FILE_COMPLETE,

    /** L2 全部文件分析完成 */
    L2_COMPLETE,

    /** L3 全局聚合完成 */
    L3_COMPLETE,

    /** 全流程完成 */
    COMPLETED,

    /** 非致命错误（单个文件失败等，不影响整体） */
    ERROR,

    /** 全流程失败（致命异常） */
    FAILED;

    /** 是否与特定文件关联 */
    public boolean isFileEvent() {
        return this == L2_FILE_START || this == L2_FILE_COMPLETE;
    }

    /** 是否表示流程结束 */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
