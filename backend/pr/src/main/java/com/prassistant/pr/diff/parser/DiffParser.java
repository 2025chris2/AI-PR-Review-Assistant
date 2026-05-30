package com.prassistant.pr.diff.parser;

import com.prassistant.pr.diff.model.DiffHunk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 底层 diff 解析工具 — 纯函数，无状态
 *
 * <p>职责：</p>
 * <ul>
 *   <li>行分类 — 判断原始 diff 中每行的类型</li>
 *   <li>Header 解析 — 从 @@ 行提取 Hunk 结构</li>
 *   <li>路径提取 — 从 diff --git / +++ 行提取文件路径</li>
 *   <li>尾部空白清理</li>
 * </ul>
 *
 * <p>所有方法均为静态纯函数，供 {@link com.prassistant.pr.diff.service.DiffSanitizer} 调用。</p>
 */
public final class DiffParser {

    // @@ -oldStart,oldCount +newStart,newCount @@ sectionHeader
    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile(
        "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$"
    );

    private DiffParser() {
        // 工具类，禁止实例化
    }

    /**
     * 分类 diff 中的单行（trimmed）
     */
    public static DiffLineType classify(String trimmedLine) {
        if (trimmedLine.startsWith("diff --git")) {
            return DiffLineType.DIFF_GIT_HEADER;
        }
        if (trimmedLine.startsWith("index ")) {
            return DiffLineType.INDEX_LINE;
        }
        if (trimmedLine.startsWith("--- ")) {
            return DiffLineType.MINUS_FILE_LINE;
        }
        if (trimmedLine.startsWith("+++ ")) {
            return DiffLineType.PLUS_FILE_LINE;
        }
        if (trimmedLine.startsWith("@@")) {
            return DiffLineType.HUNK_HEADER;
        }
        if (trimmedLine.startsWith("\\ No newline")) {
            return DiffLineType.NO_NEWLINE;
        }
        // 内容行（通过原始行前缀判断，不是 trimmed）
        // 这里仅通过 trimmed 做初步判断，完整判断由 classifyContentLine 完成
        if (trimmedLine.startsWith("new file mode")
            || trimmedLine.startsWith("deleted file mode")
            || trimmedLine.startsWith("old mode")
            || trimmedLine.startsWith("new mode")
            || trimmedLine.startsWith("rename from")
            || trimmedLine.startsWith("rename to")
            || trimmedLine.startsWith("similarity index")
            || trimmedLine.startsWith("copy from")
            || trimmedLine.startsWith("copy to")) {
            return DiffLineType.OTHER_META;
        }
        return DiffLineType.UNKNOWN;
    }

    /**
     * 判断原始行是否为有效的 Hunk 内容行
     *
     * <p>有效内容行：空格开头（上下文）、+ 开头（新增）、- 开头（删除）、空行</p>
     */
    public static boolean isContentLine(String originalLine) {
        return originalLine.startsWith(" ")
            || originalLine.startsWith("+")
            || originalLine.startsWith("-")
            || originalLine.isEmpty();
    }

    /**
     * 解析 @@ hunk header 行，返回 DiffHunk 或 null
     */
    public static DiffHunk parseHunkHeader(String line) {
        Matcher m = HUNK_HEADER_PATTERN.matcher(line);
        if (m.find()) {
            DiffHunk hunk = new DiffHunk();
            hunk.setOldStartLine(Integer.parseInt(m.group(1)));
            hunk.setOldLineCount(m.group(2) != null ? Integer.parseInt(m.group(2)) : 1);
            hunk.setNewStartLine(Integer.parseInt(m.group(3)));
            hunk.setNewLineCount(m.group(4) != null ? Integer.parseInt(m.group(4)) : 1);
            hunk.setSectionHeader(m.group(5).trim());
            return hunk;
        }
        return null;
    }

    /**
     * 从 diff --git a/path b/path 行提取 b/ 路径
     */
    public static String extractPathFromDiffGitHeader(String diffLine) {
        String content = diffLine.substring("diff --git ".length());
        String[] parts = content.split(" ");
        if (parts.length >= 2) {
            String bPath = parts[1];
            if (bPath.startsWith("b/")) {
                return bPath.substring(2);
            }
            return bPath;
        }
        return null;
    }

    /**
     * 从 +++ b/path 行提取文件路径
     */
    public static String extractPathFromPlusLine(String line) {
        String path = line.substring(4).trim();
        if (path.startsWith("b/")) {
            return path.substring(2);
        }
        return path;
    }

    /**
     * 去除行尾空白字符（空格、制表符）
     */
    public static String removeTrailingWhitespace(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            end--;
        }
        return line.substring(0, end);
    }
}
