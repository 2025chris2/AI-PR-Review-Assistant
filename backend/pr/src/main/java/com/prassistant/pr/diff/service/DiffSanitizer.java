package com.prassistant.pr.diff.service;

import com.prassistant.pr.diff.model.DiffHunk;
import com.prassistant.pr.diff.model.FileChangeType;
import com.prassistant.pr.diff.model.ParseState;
import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.diff.parser.DiffParser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 第一层：Diff 去噪引擎
 *
 * <p>使用 {@link ParseState} 状态机逐行解析 GitHub 原始 diff / patch，
 * 剔除元数据噪音，只保留 @@ 行号锚点和有效代码行。</p>
 *
 * <p>底层行分类和 header 解析委托给 {@link DiffParser}。</p>
 *
 * <h3>状态机概览</h3>
 * <pre>
 *   BETWEEN_FILES  →  IN_FILE_HEADER  →  IN_HUNK_CONTENT
 *        ↑                  │                   │
 *        └── diff --git ────┘                   │
 *                                               │
 *                          ← ← ← diff --git ← ← ┘
 * </pre>
 */
@Service
public class DiffSanitizer {

    /**
     * 处理多文件完整 diff（如通过 diff_url 获取）
     */
    public List<SanitizedDiff> sanitize(String rawDiff) {
        List<SanitizedDiff> results = new ArrayList<>();
        if (rawDiff == null || rawDiff.isBlank()) {
            return results;
        }

        String[] lines = rawDiff.split("\n");
        ParseState state = ParseState.BETWEEN_FILES;
        SanitizedDiff currentFile = null;
        DiffHunk currentHunk = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // ========== 状态：BETWEEN_FILES ==========
            if (state == ParseState.BETWEEN_FILES) {
                if (trimmed.startsWith("diff --git")) {
                    currentFile = new SanitizedDiff();
                    currentFile.setFilePath(DiffParser.extractPathFromDiffGitHeader(trimmed));
                    state = ParseState.IN_FILE_HEADER;
                } else if (trimmed.startsWith("@@")) {
                    // 单文件 patch（无 diff --git 头），直接进入 hunk
                    currentFile = new SanitizedDiff();
                    currentFile.setFilePath("unknown");
                    currentFile.setStatus(FileChangeType.MODIFIED);
                    state = ParseState.IN_HUNK_CONTENT;
                    currentHunk = DiffParser.parseHunkHeader(trimmed);
                }
                // 其他行：跳过
                continue;
            }

            // ========== 状态：IN_FILE_HEADER ==========
            if (state == ParseState.IN_FILE_HEADER) {
                if (trimmed.startsWith("index ")) {
                    continue;
                }
                if (trimmed.startsWith("--- ")) {
                    if ("--- /dev/null".equals(trimmed)) {
                        currentFile.setStatus(FileChangeType.ADDED);
                    }
                    continue;
                }
                if (trimmed.startsWith("+++ ")) {
                    if ("+++ /dev/null".equals(trimmed)) {
                        currentFile.setStatus(FileChangeType.REMOVED);
                    } else {
                        currentFile.setFilePath(DiffParser.extractPathFromPlusLine(trimmed));
                    }
                    continue;
                }
                if (trimmed.startsWith("@@")) {
                    // 文件头结束，进入 hunk
                    state = ParseState.IN_HUNK_CONTENT;
                    currentHunk = DiffParser.parseHunkHeader(trimmed);
                    continue;
                }
                // 其他元数据行：跳过
                continue;
            }

            // ========== 状态：IN_HUNK_CONTENT ==========
            if (state == ParseState.IN_HUNK_CONTENT) {
                if (trimmed.startsWith("diff --git")) {
                    // 新文件开始，保存当前文件
                    saveCurrentFile(results, currentFile, currentHunk);
                    currentFile = new SanitizedDiff();
                    currentFile.setFilePath(DiffParser.extractPathFromDiffGitHeader(trimmed));
                    currentHunk = null;
                    state = ParseState.IN_FILE_HEADER;
                    continue;
                }
                if (trimmed.startsWith("@@")) {
                    // 新 Hunk
                    saveCurrentHunk(currentFile, currentHunk);
                    currentHunk = DiffParser.parseHunkHeader(trimmed);
                    continue;
                }
                if (currentHunk != null) {
                    if (DiffParser.isContentLine(line)) {
                        currentHunk.getLines().add(DiffParser.removeTrailingWhitespace(line));
                    } else if (trimmed.startsWith("\\ No newline")) {
                        // 文件尾标记，跳过
                        continue;
                    }
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
    public SanitizedDiff sanitizeSingleFile(String filename, String rawPatch, FileChangeType status) {
        SanitizedDiff diff = sanitize(rawPatch).stream()
            .findFirst()
            .orElse(new SanitizedDiff());

        diff.setFilePath(filename);
        if (status != null) {
            diff.setStatus(status);
        }
        return diff;
    }

    // ==================== 私有编排方法 ====================

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
}
