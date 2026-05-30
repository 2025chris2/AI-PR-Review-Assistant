package com.prassistant.pr.diff.model;

/**
 * Diff 解析状态机 — 三种互斥状态
 *
 * <p>{@link com.prassistant.pr.diff.service.DiffSanitizer} 逐行解析原始 diff 时，
 * 根据当前状态决定如何处理每一行。</p>
 *
 * <h3>状态转换图</h3>
 * <pre>
 *                    diff --git
 *   BETWEEN_FILES ───────────────► IN_FILE_HEADER
 *       │                              │
 *       │ @@ 直接进入                    │ @@ 遇到（文件头结束）
 *       │                              │
 *       └──────────────────────────────┘
 *                      ▼
 *               IN_HUNK_CONTENT
 *                      │    ▲
 *                      │    │ @@ 遇到（新 hunk）
 *                      │    │ diff --git 遇到（新文件）
 *                      └────┘
 * </pre>
 */
public enum ParseState {

    /** 文件之间：就绪等待下一个文件开始（初始状态） */
    BETWEEN_FILES,

    /** 文件头解析中：正在消费 index / --- / +++ 等元数据行 */
    IN_FILE_HEADER,

    /** Hunk 内容收集中：正在收集以 ' ' / '+' / '-' 开头的代码行 */
    IN_HUNK_CONTENT
}
