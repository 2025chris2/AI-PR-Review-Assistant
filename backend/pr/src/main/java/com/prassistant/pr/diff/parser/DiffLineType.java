package com.prassistant.pr.diff.parser;

/**
 * Diff 行类型 — 原始 diff 中每一行的分类结果
 *
 * <p>供 {@link DiffParser#classify} 返回，调用方根据类型决定处理策略。</p>
 */
public enum DiffLineType {

    /** diff --git a/... b/... — 新文件开始 */
    DIFF_GIT_HEADER,

    /** index abc..def 100644 — 对象哈希 */
    INDEX_LINE,

    /** --- a/path 或 --- /dev/null — 旧文件标记 */
    MINUS_FILE_LINE,

    /** +++ b/path 或 +++ /dev/null — 新文件标记 */
    PLUS_FILE_LINE,

    /** @@ -old,count +new,count @@ context — Hunk 头 */
    HUNK_HEADER,

    /** 以空格开头的上下文行 */
    CONTEXT_LINE,

    /** 以 + 开头的新增行 */
    ADDED_LINE,

    /** 以 - 开头的删除行 */
    REMOVED_LINE,

    /** \ No newline at end of file — 文件尾标记 */
    NO_NEWLINE,

    /** 其他元数据行（new file mode / deleted file mode / rename 等） */
    OTHER_META,

    /** 无法识别的行 */
    UNKNOWN
}
