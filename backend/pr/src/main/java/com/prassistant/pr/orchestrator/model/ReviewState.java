package com.prassistant.pr.orchestrator.model;

/**
 * 分析任务生命周期状态枚举
 *
 * <p>状态流转：PENDING → FETCHING_DIFF → L1_SANITIZING → L2_ANALYZING → L3_AGGREGATING → COMPLETED
 * <br>异常时任意状态可转入 FAILED</p>
 */
public enum ReviewState {

    /** 任务已创建，等待执行 */
    PENDING,

    /** 正在从 Git 拉取原始 Diff */
    FETCHING_DIFF,

    /** L1 层：Diff 去噪与 Hunk 切分 */
    L1_SANITIZING,

    /** L2 层：逐文件并发分析（MapReduce） */
    L2_ANALYZING,

    /** L3 层：全局聚合分析 */
    L3_AGGREGATING,

    /** 分析完成 */
    COMPLETED,

    /** 分析失败（致命异常） */
    FAILED;

    /** 是否处于运行中状态 */
    public boolean isRunning() {
        return this == FETCHING_DIFF
                || this == L1_SANITIZING
                || this == L2_ANALYZING
                || this == L3_AGGREGATING;
    }

    /** 是否为终态 */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
