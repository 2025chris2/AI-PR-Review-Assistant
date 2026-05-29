package com.prassistant.pr.diff;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个 Diff Hunk（代码变更块）
 * 包含行号范围和该块内的所有代码行
 */
@Data
public class DiffHunk {
    
    // 旧文件起始行
    private int oldStartLine;
    
    // 旧文件影响行数
    private int oldLineCount;
    
    // 新文件起始行
    private int newStartLine;
    
    // 新文件影响行数
    private int newLineCount;
    
    // @@ 后面的上下文标记（如类名/函数名）
    private String sectionHeader;
    
    // 该 Hunk 内的所有代码行（含上下文和 +/- 变更）
    private List<String> lines = new ArrayList<>();

}