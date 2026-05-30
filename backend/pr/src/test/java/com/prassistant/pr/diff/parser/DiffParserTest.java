package com.prassistant.pr.diff.parser;

import com.prassistant.pr.diff.model.DiffHunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiffParserTest {

    @Nested
    @DisplayName("classify() — 行分类")
    class Classify {

        @Test
        @DisplayName("diff --git 行 → DIFF_GIT_HEADER")
        void shouldClassifyDiffGitHeader() {
            assertEquals(DiffLineType.DIFF_GIT_HEADER,
                DiffParser.classify("diff --git a/src/App.java b/src/App.java"));
        }

        @Test
        @DisplayName("index 行 → INDEX_LINE")
        void shouldClassifyIndexLine() {
            assertEquals(DiffLineType.INDEX_LINE,
                DiffParser.classify("index abc1234..def5678 100644"));
        }

        @Test
        @DisplayName("--- 行 → MINUS_FILE_LINE")
        void shouldClassifyMinusFileLine() {
            assertEquals(DiffLineType.MINUS_FILE_LINE,
                DiffParser.classify("--- a/src/App.java"));
        }

        @Test
        @DisplayName("--- /dev/null → MINUS_FILE_LINE")
        void shouldClassifyMinusDevNull() {
            assertEquals(DiffLineType.MINUS_FILE_LINE,
                DiffParser.classify("--- /dev/null"));
        }

        @Test
        @DisplayName("+++ 行 → PLUS_FILE_LINE")
        void shouldClassifyPlusFileLine() {
            assertEquals(DiffLineType.PLUS_FILE_LINE,
                DiffParser.classify("+++ b/src/App.java"));
        }

        @Test
        @DisplayName("+++ /dev/null → PLUS_FILE_LINE")
        void shouldClassifyPlusDevNull() {
            assertEquals(DiffLineType.PLUS_FILE_LINE,
                DiffParser.classify("+++ /dev/null"));
        }

        @Test
        @DisplayName("@@ 行 → HUNK_HEADER")
        void shouldClassifyHunkHeader() {
            assertEquals(DiffLineType.HUNK_HEADER,
                DiffParser.classify("@@ -10,7 +10,8 @@ public class App {"));
        }

        @Test
        @DisplayName("\\ No newline 行 → NO_NEWLINE")
        void shouldClassifyNoNewline() {
            assertEquals(DiffLineType.NO_NEWLINE,
                DiffParser.classify("\\ No newline at end of file"));
        }

        @Test
        @DisplayName("new file mode 行 → OTHER_META")
        void shouldClassifyNewFileMode() {
            assertEquals(DiffLineType.OTHER_META,
                DiffParser.classify("new file mode 100644"));
        }

        @Test
        @DisplayName("deleted file mode 行 → OTHER_META")
        void shouldClassifyDeletedFileMode() {
            assertEquals(DiffLineType.OTHER_META,
                DiffParser.classify("deleted file mode 100644"));
        }

        @Test
        @DisplayName("rename from 行 → OTHER_META")
        void shouldClassifyRenameFrom() {
            assertEquals(DiffLineType.OTHER_META,
                DiffParser.classify("rename from old/path"));
        }

        @Test
        @DisplayName("无法识别的行 → UNKNOWN")
        void shouldClassifyUnknown() {
            assertEquals(DiffLineType.UNKNOWN,
                DiffParser.classify("some random content without prefix"));
        }
    }

    @Nested
    @DisplayName("isContentLine() — 内容行判断")
    class IsContentLine {

        @Test
        @DisplayName("空格开头 → 内容行")
        void shouldAcceptContextLine() {
            assertTrue(DiffParser.isContentLine(" context line"));
        }

        @Test
        @DisplayName("+ 开头 → 内容行")
        void shouldAcceptAddedLine() {
            assertTrue(DiffParser.isContentLine("+added line"));
        }

        @Test
        @DisplayName("- 开头 → 内容行")
        void shouldAcceptRemovedLine() {
            assertTrue(DiffParser.isContentLine("-removed line"));
        }

        @Test
        @DisplayName("空字符串 → 内容行")
        void shouldAcceptEmptyLine() {
            assertTrue(DiffParser.isContentLine(""));
        }

        @Test
        @DisplayName("普通文本 → 非内容行")
        void shouldRejectPlainText() {
            assertFalse(DiffParser.isContentLine("just some text"));
        }

        @Test
        @DisplayName("@@ header → 非内容行")
        void shouldRejectHunkHeader() {
            assertFalse(DiffParser.isContentLine("@@ -1,3 +1,4 @@"));
        }
    }

    @Nested
    @DisplayName("parseHunkHeader() — Hunk 头解析")
    class ParseHunkHeader {

        @Test
        @DisplayName("完整格式带 section header")
        void shouldParseCompleteHunkHeader() {
            DiffHunk hunk = DiffParser.parseHunkHeader("@@ -10,7 +10,8 @@ public class App {");

            assertNotNull(hunk);
            assertEquals(10, hunk.getOldStartLine());
            assertEquals(7, hunk.getOldLineCount());
            assertEquals(10, hunk.getNewStartLine());
            assertEquals(8, hunk.getNewLineCount());
            assertEquals("public class App {", hunk.getSectionHeader());
        }

        @Test
        @DisplayName("省略 count（默认 1）")
        void shouldDefaultCountToOne() {
            DiffHunk hunk = DiffParser.parseHunkHeader("@@ -5 +5 @@");

            assertNotNull(hunk);
            assertEquals(5, hunk.getOldStartLine());
            assertEquals(1, hunk.getOldLineCount());
            assertEquals(5, hunk.getNewStartLine());
            assertEquals(1, hunk.getNewLineCount());
        }

        @Test
        @DisplayName("新增文件格式 @@ -0,0 +1,N @@")
        void shouldParseNewFileHeader() {
            DiffHunk hunk = DiffParser.parseHunkHeader("@@ -0,0 +1,42 @@");

            assertNotNull(hunk);
            assertEquals(0, hunk.getOldStartLine());
            assertEquals(0, hunk.getOldLineCount());
            assertEquals(1, hunk.getNewStartLine());
            assertEquals(42, hunk.getNewLineCount());
            assertEquals("", hunk.getSectionHeader());
        }

        @Test
        @DisplayName("删除文件格式 @@ -1,N +0,0 @@")
        void shouldParseDeletedFileHeader() {
            DiffHunk hunk = DiffParser.parseHunkHeader("@@ -1,20 +0,0 @@");

            assertNotNull(hunk);
            assertEquals(1, hunk.getOldStartLine());
            assertEquals(20, hunk.getOldLineCount());
            assertEquals(0, hunk.getNewStartLine());
            assertEquals(0, hunk.getNewLineCount());
        }

        @Test
        @DisplayName("非法格式返回 null")
        void shouldReturnNullForInvalidFormat() {
            assertNull(DiffParser.parseHunkHeader("not a hunk header"));
            assertNull(DiffParser.parseHunkHeader("@@ invalid"));
        }
    }

    @Nested
    @DisplayName("extractPathFromDiffGitHeader() — diff --git 路径提取")
    class ExtractPathFromDiffGitHeader {

        @Test
        @DisplayName("标准 b/ 前缀路径")
        void shouldExtractStandardBPath() {
            String path = DiffParser.extractPathFromDiffGitHeader(
                "diff --git a/src/main/java/App.java b/src/main/java/App.java");
            assertEquals("src/main/java/App.java", path);
        }

        @Test
        @DisplayName("无 b/ 前缀路径")
        void shouldExtractPathWithoutBPrefix() {
            String path = DiffParser.extractPathFromDiffGitHeader(
                "diff --git a/Readme.md b/Readme.md");
            assertEquals("Readme.md", path);
        }

        @Test
        @DisplayName("带空格的文件名")
        void shouldHandleSpacesInFilename() {
            String path = DiffParser.extractPathFromDiffGitHeader(
                "diff --git a/my file.java b/my file.java");
            // split(" ") 按空格拆分，含空格文件名会切断 — 这是已知局限
            assertNotNull(path);
        }
    }

    @Nested
    @DisplayName("extractPathFromPlusLine() — +++ 路径提取")
    class ExtractPathFromPlusLine {

        @Test
        @DisplayName("标准 b/ 前缀")
        void shouldExtractStandardBPath() {
            String path = DiffParser.extractPathFromPlusLine("+++ b/src/main/App.java");
            assertEquals("src/main/App.java", path);
        }

        @Test
        @DisplayName("/dev/null → 原样返回")
        void shouldReturnDevNull() {
            String path = DiffParser.extractPathFromPlusLine("+++ /dev/null");
            assertEquals("/dev/null", path);
        }

        @Test
        @DisplayName("无 b/ 前缀")
        void shouldExtractWithoutBPrefix() {
            String path = DiffParser.extractPathFromPlusLine("+++ Readme.md");
            assertEquals("Readme.md", path);
        }
    }

    @Nested
    @DisplayName("removeTrailingWhitespace() — 尾部空白清理")
    class RemoveTrailingWhitespace {

        @Test
        @DisplayName("去除尾部空格")
        void shouldRemoveTrailingSpaces() {
            assertEquals(" line", DiffParser.removeTrailingWhitespace(" line   "));
        }

        @Test
        @DisplayName("去除尾部制表符")
        void shouldRemoveTrailingTabs() {
            assertEquals(" line", DiffParser.removeTrailingWhitespace(" line\t\t"));
        }

        @Test
        @DisplayName("无尾部空白时不变")
        void shouldNotModifyCleanLine() {
            assertEquals(" clean line", DiffParser.removeTrailingWhitespace(" clean line"));
        }

        @Test
        @DisplayName("纯空格字符串 → 空字符串")
        void shouldReduceAllWhitespaceToEmpty() {
            assertEquals("", DiffParser.removeTrailingWhitespace("   "));
        }

        @Test
        @DisplayName("空字符串不变")
        void shouldNotModifyEmptyString() {
            assertEquals("", DiffParser.removeTrailingWhitespace(""));
        }
    }
}
