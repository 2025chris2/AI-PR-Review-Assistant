package com.prassistant.pr.diff.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个文件的去噪结果
 */
@Data
public class SanitizedDiff {

    /** 文件路径（如 src/main/java/UserService.java） */
    private String filePath;

    /** 变更状态：added / removed / modified / renamed */
    private String status = "modified";

    /** 去噪后的完整 diff 文本（可直接用于 AI 输入） */
    private String sanitizedContent;

    /** 解析出的所有 Hunk（第二层分块时直接复用） */
    private List<DiffHunk> hunks = new ArrayList<>();

    /** 原始 patch 行数（含元数据） */
    private int originalLineCount;

    /** 去噪后有效代码行数 */
    private int sanitizedLineCount;

    /** 去噪节省比例（0.0 ~ 1.0） */
    private double savingsRatio;

}
