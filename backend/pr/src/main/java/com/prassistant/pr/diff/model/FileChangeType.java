package com.prassistant.pr.diff.model;

/**
 * 文件变更类型 — 对应 GitHub diff 中的文件状态
 */
public enum FileChangeType {

    ADDED("added"),
    REMOVED("removed"),
    MODIFIED("modified"),
    RENAMED("renamed");

    private final String value;

    FileChangeType(String value) {
        this.value = value;
    }

    /** 返回原始字符串表示（如 "added"） */
    public String getValue() {
        return value;
    }
}
