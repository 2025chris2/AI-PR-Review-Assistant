package com.prassistant.pr.diff;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第一层：Diff 去噪引擎
 * 
 * 职责：
 * 1. 解析 GitHub 返回的原始 diff / patch 格式
 * 2. 删除元数据噪音（diff --git、index、---、+++ 等）
 * 3. 保留 @@ 行号锚点和有效代码行
 * 4. 提取 Hunk 边界，为第二层文件分块做准备
 */
@Service
public class DiffSanitizer {

    // @@ -oldStart,oldCount +newStart,newCount @@ sectionHeader
    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile(
        "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$"
    );

    /**
     * 处理多文件完整 diff（如通过 diff_url 获取）
     */
    public List<SanitizedDiff> sanitize(String rawDiff) {
        List<SanitizedDiff> results = new ArrayList<>();
        if (rawDiff == null || rawDiff.isBlank()) {
            return results;
        }

        String[] lines = rawDiff.split("\n");
        SanitizedDiff currentFile = null;
        DiffHunk currentHunk = null;
        boolean inFileHeader = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // ========== 1. 新文件开始（diff --git） ==========
            if (trimmed.startsWith("diff --git")) {
                saveCurrentFile(results, currentFile, currentHunk);
                currentFile = createFileFromDiffHeader(trimmed);
                inFileHeader = true;
                currentHunk = null;
                continue;
            }

            // ========== 2. 单文件 patch（无 diff --git 头） ==========
            if (currentFile == null) {
                if (trimmed.startsWith("@@")) {
                    // 直接以 @@ 开头，说明是单文件 patch
                    currentFile = new SanitizedDiff();
                    currentFile.setFilePath("unknown");
                    currentFile.setStatus("modified");
                    inFileHeader = false;
                    // 不 continue，让同一行进入下方的 hunk 解析
                } else {
                    continue; // 跳过无关前缀
                }
            }

            // ========== 3. 文件头阶段（跳过元数据） ==========
            if (inFileHeader) {
                if (trimmed.startsWith("index ")) {
                    continue;
                }
                if (trimmed.startsWith("--- ")) {
                    if ("--- /dev/null".equals(trimmed)) {
                        currentFile.setStatus("added");
                    }
                    continue;
                }
                if (trimmed.startsWith("+++ ")) {
                    if ("+++ /dev/null".equals(trimmed)) {
                        currentFile.setStatus("removed");
                    } else {
                        currentFile.setFilePath(extractPathFromPlusLine(trimmed));
                    }
                    continue;
                }
                // 遇到第一个 @@，文件头结束
                if (trimmed.startsWith("@@")) {
                    inFileHeader = false;
                    // fall through 到下方 hunk 解析
                } else {
                    continue;
                }
            }

            // ========== 4. Hunk 头解析 ==========
            if (trimmed.startsWith("@@")) {
                saveCurrentHunk(currentFile, currentHunk);
                currentHunk = parseHunkHeader(trimmed);
                continue;
            }

            // ========== 5. Hunk 内容行 ==========
            if (currentHunk != null) {
                // 有效内容行：空格开头（上下文）、+开头（新增）、-开头（删除）
                // 空行也保留（某些 diff 中空行无前导空格）
                if (line.startsWith(" ") || line.startsWith("+") || line.startsWith("-") || line.isEmpty()) {
                    currentHunk.getLines().add(removeTrailingWhitespace(line));
                }
                // 跳过 "\ No newline at end of file"
                else if (trimmed.startsWith("\\ No newline")) {
                    continue;
                }
            }
        }

        // 收尾最后一个文件
        saveCurrentFile(results, currentFile, currentHunk);

        // 构建 sanitizedContent 和统计
        for (SanitizedDiff diff : results) {
            buildSanitizedContent(diff);
            diff.setOriginalLineCount(lines.length);
        }

        return results;
    }

    /**
     * 处理单文件 patch（如 GitHub 文件列表 API 返回的单个 patch 字段）
     * 调用方已知文件名，直接传入
     */
    public SanitizedDiff sanitizeSingleFile(String filename, String rawPatch, String status) {
        SanitizedDiff diff = sanitize(rawPatch).stream()
            .findFirst()
            .orElse(new SanitizedDiff());
        
        diff.setFilePath(filename);
        if (status != null) {
            diff.setStatus(status);
        }
        return diff;
    }

    // ==================== 私有工具方法 ====================

    private SanitizedDiff createFileFromDiffHeader(String diffLine) {
        SanitizedDiff diff = new SanitizedDiff();
        // diff --git a/path b/path，从 b/ 路径提取
        String content = diffLine.substring("diff --git ".length());
        String[] parts = content.split(" ");
        if (parts.length >= 2) {
            String bPath = parts[1];
            if (bPath.startsWith("b/")) {
                diff.setFilePath(bPath.substring(2));
            } else {
                diff.setFilePath(bPath);
            }
        }
        return diff;
    }

    private String extractPathFromPlusLine(String line) {
        // +++ b/src/main/java/...
        String path = line.substring(4).trim();
        if (path.startsWith("b/")) {
            return path.substring(2);
        }
        return path;
    }

    private DiffHunk parseHunkHeader(String line) {
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

    private void saveCurrentHunk(SanitizedDiff file, DiffHunk hunk) {
        if (hunk != null && !hunk.getLines().isEmpty()) {
            file.getHunks().add(hunk);
        }
    }

    private void saveCurrentFile(List<SanitizedDiff> results, SanitizedDiff file, DiffHunk lastHunk) {
        if (file != null) {
            saveCurrentHunk(file, lastHunk);
            results.add(file);
        }
    }

    private void buildSanitizedContent(SanitizedDiff diff) {
        StringBuilder sb = new StringBuilder();
        
        for (DiffHunk hunk : diff.getHunks()) {
            sb.append(String.format("@@ -%d,%d +%d,%d @@ %s%n",
                hunk.getOldStartLine(),
                hunk.getOldLineCount(),
                hunk.getNewStartLine(),
                hunk.getNewLineCount(),
                hunk.getSectionHeader()
            ));
            
            for (String line : hunk.getLines()) {
                sb.append(line).append("\n");
            }
            sb.append("\n");
        }

        String content = sb.toString().trim();
        diff.setSanitizedContent(content);
        
        // 统计
        int codeLines = diff.getHunks().stream()
            .mapToInt(h -> h.getLines().size())
            .sum();
        diff.setSanitizedLineCount(codeLines);
        
        if (diff.getOriginalLineCount() > 0) {
            diff.setSavingsRatio(1.0 - (double) codeLines / diff.getOriginalLineCount());
        }
    }

    private String removeTrailingWhitespace(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            end--;
        }
        return line.substring(0, end);
    }
}
