package com.prassistant.pr.diff.service;

import com.prassistant.pr.diff.model.DiffHunk;
import com.prassistant.pr.diff.model.SanitizedDiff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffSanitizerTest {

    private DiffSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new DiffSanitizer();
    }

    // ==================== sanitize() 边界条件 ====================

    @Nested
    @DisplayName("sanitize() 边界条件")
    class SanitizeEdgeCases {

        @Test
        @DisplayName("null 输入应返回空列表")
        void shouldReturnEmptyListForNullInput() {
            List<SanitizedDiff> results = sanitizer.sanitize(null);
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("空白字符串输入应返回空列表")
        void shouldReturnEmptyListForBlankInput() {
            List<SanitizedDiff> results = sanitizer.sanitize("   \n  \n  ");
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("空字符串输入应返回空列表")
        void shouldReturnEmptyListForEmptyInput() {
            List<SanitizedDiff> results = sanitizer.sanitize("");
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }
    }

    // ==================== 单文件 diff --git 格式 ====================

    @Nested
    @DisplayName("单文件 diff --git 格式")
    class SingleFileDiffGit {

        @Test
        @DisplayName("应正确解析完整的 diff --git 格式")
        void shouldParseCompleteDiffGitFormat() {
            String rawDiff = """
                diff --git a/src/main/java/UserService.java b/src/main/java/UserService.java
                index abc1234..def5678 100644
                --- a/src/main/java/UserService.java
                +++ b/src/main/java/UserService.java
                @@ -10,7 +10,8 @@ public class UserService {
                     private UserRepository userRepository;
                 \s
                     public User findById(Long id) {
                -        return userRepository.findOne(id);
                +        return userRepository.findById(id)
                +            .orElseThrow(() -> new NotFoundException("User not found"));
                     }
                 }
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals(1, results.size());
            SanitizedDiff diff = results.get(0);
            assertEquals("src/main/java/UserService.java", diff.getFilePath());
            assertEquals("modified", diff.getStatus());
            assertNotNull(diff.getHunks());
            assertEquals(1, diff.getHunks().size());

            DiffHunk hunk = diff.getHunks().get(0);
            assertEquals(10, hunk.getOldStartLine());
            assertEquals(7, hunk.getOldLineCount());
            assertEquals(10, hunk.getNewStartLine());
            assertEquals(8, hunk.getNewLineCount());
            assertEquals("public class UserService {", hunk.getSectionHeader());

            // 验证 hunk 内容行
            assertEquals(8, hunk.getLines().size());
            assertEquals("     private UserRepository userRepository;", hunk.getLines().get(0));
            assertEquals("", hunk.getLines().get(1)); // 空格行经 removeTrailingWhitespace 后变为空字符串
            assertEquals("     public User findById(Long id) {", hunk.getLines().get(2));
            assertEquals("-        return userRepository.findOne(id);", hunk.getLines().get(3));
            assertEquals("+        return userRepository.findById(id)", hunk.getLines().get(4));
            assertEquals("+            .orElseThrow(() -> new NotFoundException(\"User not found\"));", hunk.getLines().get(5));
            assertEquals("     }", hunk.getLines().get(6));
            assertEquals(" }", hunk.getLines().get(7));

            // 验证统计
            assertNotNull(diff.getSanitizedContent());
            assertTrue(diff.getSanitizedContent().contains("@@ -10,7 +10,8 @@ public class UserService {"));
            assertTrue(diff.getSanitizedContent().contains("-        return userRepository.findOne(id);"));
            assertEquals(8, diff.getSanitizedLineCount());
            assertEquals(13, diff.getOriginalLineCount());
            // Note: savingsRatio is 0.0 because buildSanitizedContent runs before setOriginalLineCount
            assertEquals(0.0, diff.getSavingsRatio());
        }

        @Test
        @DisplayName("应正确处理新增文件（--- /dev/null）")
        void shouldHandleAddedFile() {
            String rawDiff = """
                diff --git a/src/main/java/NewService.java b/src/main/java/NewService.java
                new file mode 100644
                index 0000000..abc1234
                --- /dev/null
                +++ b/src/main/java/NewService.java
                @@ -0,0 +1,5 @@
                +package com.example;
                +
                +public class NewService {
                +    public void doSomething() {}
                +}
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals(1, results.size());
            SanitizedDiff diff = results.get(0);
            assertEquals("src/main/java/NewService.java", diff.getFilePath());
            assertEquals("added", diff.getStatus());

            assertEquals(1, diff.getHunks().size());
            DiffHunk hunk = diff.getHunks().get(0);
            assertEquals(0, hunk.getOldStartLine());
            assertEquals(0, hunk.getOldLineCount());
            assertEquals(1, hunk.getNewStartLine());
            assertEquals(5, hunk.getNewLineCount());
        }

        @Test
        @DisplayName("应正确处理删除文件（+++ /dev/null）")
        void shouldHandleRemovedFile() {
            String rawDiff = """
                diff --git a/src/main/java/OldService.java b/src/main/java/OldService.java
                deleted file mode 100644
                index abc1234..0000000
                --- a/src/main/java/OldService.java
                +++ /dev/null
                @@ -1,5 +0,0 @@
                -package com.example;
                -
                -public class OldService {
                -    public void doSomething() {}
                -}
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals(1, results.size());
            SanitizedDiff diff = results.get(0);
            assertEquals("removed", diff.getStatus());
            assertEquals(5, diff.getSanitizedLineCount());
        }
    }

    // ==================== 单文件 patch（无 diff --git 头） ====================

    @Nested
    @DisplayName("单文件 patch（无 diff --git 头）")
    class SingleFilePatch {

        @Test
        @DisplayName("应正确解析直接以 @@ 开头的单文件 patch")
        void shouldParseRawPatchWithoutDiffGitHeader() {
            String rawPatch = """
                @@ -15,7 +15,7 @@ public class OrderService {
                         Order order = orderRepository.findById(id);
                -        order.setStatus(OrderStatus.CANCELLED);
                +        order.setStatus(OrderStatus.CANCELED);
                         orderRepository.save(order);
                     }
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawPatch);

            assertEquals(1, results.size());
            SanitizedDiff diff = results.get(0);
            assertEquals("unknown", diff.getFilePath());
            assertEquals("modified", diff.getStatus());

            assertEquals(1, diff.getHunks().size());
            DiffHunk hunk = diff.getHunks().get(0);
            assertEquals(15, hunk.getOldStartLine());
            assertEquals(7, hunk.getOldLineCount());
            assertEquals(15, hunk.getNewStartLine());
            assertEquals(7, hunk.getNewLineCount());

            List<String> lines = hunk.getLines();
            assertEquals(5, lines.size());
            assertEquals("         Order order = orderRepository.findById(id);", lines.get(0));
            assertEquals("-        order.setStatus(OrderStatus.CANCELLED);", lines.get(1));
            assertEquals("+        order.setStatus(OrderStatus.CANCELED);", lines.get(2));
            assertEquals("         orderRepository.save(order);", lines.get(3));
            assertEquals("     }", lines.get(4));
        }
    }

    // ==================== 多文件 diff ====================

    @Nested
    @DisplayName("多文件 diff")
    class MultiFileDiff {

        @Test
        @DisplayName("应正确解析包含多个文件的 diff")
        void shouldParseMultiFileDiff() {
            String rawDiff = """
                diff --git a/src/main/java/UserService.java b/src/main/java/UserService.java
                index 111..222 100644
                --- a/src/main/java/UserService.java
                +++ b/src/main/java/UserService.java
                @@ -10,4 +10,4 @@ public class UserService {
                     public User findById(Long id) {
                -        return userRepository.findOne(id);
                +        return userRepository.findById(id).orElse(null);
                     }
                 }
                diff --git a/src/main/java/OrderService.java b/src/main/java/OrderService.java
                index 333..444 100644
                --- a/src/main/java/OrderService.java
                +++ b/src/main/java/OrderService.java
                @@ -5,3 +5,4 @@ public class OrderService {
                     public Order create(Order order) {
                +        validateOrder(order);
                         return orderRepository.save(order);
                     }
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals(2, results.size());

            SanitizedDiff diff1 = results.get(0);
            assertEquals("src/main/java/UserService.java", diff1.getFilePath());
            assertEquals(1, diff1.getHunks().size());

            SanitizedDiff diff2 = results.get(1);
            assertEquals("src/main/java/OrderService.java", diff2.getFilePath());
            assertEquals(1, diff2.getHunks().size());
        }
    }

    // ==================== 多个 Hunk ====================

    @Nested
    @DisplayName("多个 Hunk 处理")
    class MultipleHunks {

        @Test
        @DisplayName("应正确解析同一文件中的多个 Hunk")
        void shouldParseMultipleHunksInSameFile() {
            String rawDiff = """
                diff --git a/src/main/java/UserService.java b/src/main/java/UserService.java
                index 111..222 100644
                --- a/src/main/java/UserService.java
                +++ b/src/main/java/UserService.java
                @@ -1,5 +1,6 @@
                 import java.util.List;
                +import java.util.Optional;
                \s
                 public class UserService {
                @@ -20,4 +21,5 @@ public class UserService {
                     public void delete(Long id) {
                +        log.info("Deleting user: {}", id);
                         userRepository.deleteById(id);
                     }
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals(1, results.size());
            SanitizedDiff diff = results.get(0);
            assertEquals(2, diff.getHunks().size());

            DiffHunk hunk1 = diff.getHunks().get(0);
            assertEquals(1, hunk1.getOldStartLine());

            DiffHunk hunk2 = diff.getHunks().get(1);
            assertEquals(20, hunk2.getOldStartLine());

            String content = diff.getSanitizedContent();
            assertTrue(content.contains("@@ -1,5 +1,6 @@"));
            assertTrue(content.contains("@@ -20,4 +21,5 @@ public class UserService {"));
        }
    }

    // ==================== Hunk Header 解析 ====================

    @Nested
    @DisplayName("Hunk Header 解析")
    class HunkHeaderParsing {

        @Test
        @DisplayName("应正确解析带 section header 的 hunk")
        void shouldParseHunkWithSectionHeader() {
            String rawDiff = """
                diff --git a/src/App.java b/src/App.java
                index 111..222 100644
                --- a/src/App.java
                +++ b/src/App.java
                @@ -30,10 +30,12 @@ public class App {
                     public static void main(String[] args) {
                +        System.out.println("Hello");
                     }
                 }
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals(1, results.size());
            DiffHunk hunk = results.get(0).getHunks().get(0);
            assertEquals("public class App {", hunk.getSectionHeader());
        }

        @Test
        @DisplayName("应正确解析不带 section header 的 hunk")
        void shouldParseHunkWithoutSectionHeader() {
            String rawDiff = """
                diff --git a/src/App.java b/src/App.java
                index 111..222 100644
                --- a/src/App.java
                +++ b/src/App.java
                @@ -30,10 +30,12 @@
                     public static void main(String[] args) {
                +        System.out.println("Hello");
                     }
                 }
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals(1, results.size());
            DiffHunk hunk = results.get(0).getHunks().get(0);
            assertEquals("", hunk.getSectionHeader());
        }

        @Test
        @DisplayName("应正确处理省略 count 的 @@ header（count 默认为 1）")
        void shouldDefaultCountToOneWhenOmitted() {
            String rawDiff = """
                diff --git a/test.java b/test.java
                index 111..222 100644
                --- a/test.java
                +++ b/test.java
                @@ -5 +5 @@
                -old
                +new
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals(1, results.size());
            DiffHunk hunk = results.get(0).getHunks().get(0);
            assertEquals(5, hunk.getOldStartLine());
            assertEquals(1, hunk.getOldLineCount());
            assertEquals(5, hunk.getNewStartLine());
            assertEquals(1, hunk.getNewLineCount());
            assertEquals(2, hunk.getLines().size());
            assertEquals("-old", hunk.getLines().get(0));
            assertEquals("+new", hunk.getLines().get(1));
        }

        @Test
        @DisplayName("应正确处理零行号（如新增文件 @@ -0,0 +1,N @@）")
        void shouldHandleZeroLineNumbers() {
            String rawDiff = """
                diff --git a/newfile.java b/newfile.java
                new file mode 100644
                index 0000000..abc1234
                --- /dev/null
                +++ b/newfile.java
                @@ -0,0 +1,3 @@
                +line1
                +line2
                +line3
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals(1, results.size());
            DiffHunk hunk = results.get(0).getHunks().get(0);
            assertEquals(0, hunk.getOldStartLine());
            assertEquals(0, hunk.getOldLineCount());
            assertEquals(1, hunk.getNewStartLine());
            assertEquals(3, hunk.getNewLineCount());
            assertEquals(3, hunk.getLines().size());
        }
    }

    // ==================== 内容行处理 ====================

    @Nested
    @DisplayName("内容行处理")
    class ContentLineProcessing {

        @Test
        @DisplayName("应保留上下文行、新增行、删除行")
        void shouldPreserveContextAddedAndRemovedLines() {
            String rawDiff = """
                diff --git a/test.java b/test.java
                index 111..222 100644
                --- a/test.java
                +++ b/test.java
                @@ -1,5 +1,5 @@
                 context line
                +added line
                -removed line
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            List<String> lines = results.get(0).getHunks().get(0).getLines();
            assertEquals(3, lines.size());
            assertEquals(" context line", lines.get(0));
            assertEquals("+added line", lines.get(1));
            assertEquals("-removed line", lines.get(2));
        }

        @Test
        @DisplayName("应保留 hunk 内的空行")
        void shouldPreserveEmptyLinesInHunk() {
            String rawDiff = """
                diff --git a/test.java b/test.java
                index 111..222 100644
                --- a/test.java
                +++ b/test.java
                @@ -1,3 +1,3 @@
                 line1

                 line3
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            List<String> lines = results.get(0).getHunks().get(0).getLines();
            assertEquals(3, lines.size());
            assertEquals(" line1", lines.get(0));
            assertEquals("", lines.get(1));
            assertEquals(" line3", lines.get(2));
        }

        @Test
        @DisplayName("应跳过 \\ No newline at end of file 标记")
        void shouldSkipNoNewlineAtEndOfFile() {
            String rawDiff = """
                diff --git a/test.java b/test.java
                index 111..222 100644
                --- a/test.java
                +++ b/test.java
                @@ -1,3 +1,3 @@
                 line1
                 line2
                -line3
                \\ No newline at end of file
                +line3
                \\ No newline at end of file
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            List<String> lines = results.get(0).getHunks().get(0).getLines();
            assertEquals(4, lines.size());
            assertEquals(" line1", lines.get(0));
            assertEquals(" line2", lines.get(1));
            assertEquals("-line3", lines.get(2));
            assertEquals("+line3", lines.get(3));
            for (String line : lines) {
                assertFalse(line.contains("No newline"));
            }
        }

        @Test
        @DisplayName("应跳过 hunk 内的无关行")
        void shouldSkipIrrelevantLines() {
            String rawDiff = """
                diff --git a/test.java b/test.java
                index 111..222 100644
                --- a/test.java
                +++ b/test.java
                @@ -1,3 +1,3 @@
                 valid context
                some random text
                +valid addition
                another junk
                 valid context 2
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            List<String> lines = results.get(0).getHunks().get(0).getLines();
            assertEquals(3, lines.size());
            assertEquals(" valid context", lines.get(0));
            assertEquals("+valid addition", lines.get(1));
            assertEquals(" valid context 2", lines.get(2));
        }
    }

    // ==================== 文件路径提取 ====================

    @Nested
    @DisplayName("文件路径提取")
    class FilePathExtraction {

        @Test
        @DisplayName("应从 +++ b/ 行提取文件路径")
        void shouldExtractPathFromPlusLine() {
            String rawDiff = """
                diff --git a/src/main/java/com/example/App.java b/src/main/java/com/example/App.java
                index 111..222 100644
                --- a/src/main/java/com/example/App.java
                +++ b/src/main/java/com/example/App.java
                @@ -1,1 +1,1 @@
                 unchanged
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals("src/main/java/com/example/App.java", results.get(0).getFilePath());
        }

        @Test
        @DisplayName("应处理不带 b/ 前缀的 +++ 行")
        void shouldHandlePlusLineWithoutBPrefix() {
            String rawDiff = """
                diff --git a/Readme.md b/Readme.md
                index 111..222 100644
                --- a/Readme.md
                +++ b/Readme.md
                @@ -1,1 +1,1 @@
                 unchanged
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals("Readme.md", results.get(0).getFilePath());
        }
    }

    // ==================== 尾部空格去除 ====================

    @Nested
    @DisplayName("尾部空格去除")
    class TrailingWhitespaceRemoval {

        @Test
        @DisplayName("应去除行尾的空白字符")
        void shouldRemoveTrailingWhitespace() {
            String rawDiff = """
                diff --git a/test.java b/test.java
                index 111..222 100644
                --- a/test.java
                +++ b/test.java
                @@ -1,2 +1,2 @@
                 line with spaces    \s
                +new line with tabs\t\t
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            List<String> lines = results.get(0).getHunks().get(0).getLines();
            assertEquals(" line with spaces", lines.get(0));
            assertEquals("+new line with tabs", lines.get(1));
        }
    }

    // ==================== 统计信息 ====================

    @Nested
    @DisplayName("统计信息")
    class Statistics {

        @Test
        @DisplayName("应正确计算 sanitizedLineCount")
        void shouldCalculateStatisticsCorrectly() {
            String rawDiff = """
                diff --git a/src/main/java/UserService.java b/src/main/java/UserService.java
                index abc1234..def5678 100644
                --- a/src/main/java/UserService.java
                +++ b/src/main/java/UserService.java
                @@ -10,7 +10,8 @@ public class UserService {
                     private UserRepository userRepository;
                \s
                     public User findById(Long id) {
                -        return userRepository.findOne(id);
                +        return userRepository.findById(id)
                +            .orElseThrow(() -> new NotFoundException("User not found"));
                     }
                 }
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            SanitizedDiff diff = results.get(0);
            assertEquals(8, diff.getSanitizedLineCount());
            assertEquals(13, diff.getOriginalLineCount());
            assertEquals(0.0, diff.getSavingsRatio(), 0.001);
        }

        @Test
        @DisplayName("空 Hunk 场景")
        void shouldHandleEmptyHunk() {
            String rawDiff = """
                diff --git a/empty.java b/empty.java
                index 111..222 100644
                --- a/empty.java
                +++ b/empty.java
                @@ -1,1 +1,1 @@
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            SanitizedDiff diff = results.get(0);
            assertEquals(0, diff.getSanitizedLineCount());
            assertEquals(0.0, diff.getSavingsRatio(), 0.001);
            assertEquals("", diff.getSanitizedContent());
        }
    }

    // ==================== sanitizeSingleFile() ====================

    @Nested
    @DisplayName("sanitizeSingleFile() 方法")
    class SanitizeSingleFileMethod {

        @Test
        @DisplayName("应正确设置文件名和状态")
        void shouldSetFileNameAndStatus() {
            String rawPatch = """
                @@ -10,7 +10,8 @@ public class UserService {
                     public User findById(Long id) {
                -        return userRepository.findOne(id);
                +        return userRepository.findById(id).orElse(null);
                     }
                 }
                """;

            SanitizedDiff diff = sanitizer.sanitizeSingleFile(
                "src/main/java/UserService.java", rawPatch, "modified");

            assertEquals("src/main/java/UserService.java", diff.getFilePath());
            assertEquals("modified", diff.getStatus());
            assertEquals(1, diff.getHunks().size());
        }

        @Test
        @DisplayName("status 为 null 时应保留已有状态")
        void shouldKeepExistingStatusWhenNull() {
            String rawPatch = """
                @@ -1,1 +1,1 @@
                -old
                +new
                """;

            SanitizedDiff diff = sanitizer.sanitizeSingleFile(
                "some/File.java", rawPatch, null);

            assertEquals("some/File.java", diff.getFilePath());
            assertEquals("modified", diff.getStatus());
        }

        @Test
        @DisplayName("空 patch 应返回空的 SanitizedDiff")
        void shouldReturnEmptyDiffForEmptyPatch() {
            SanitizedDiff diff = sanitizer.sanitizeSingleFile(
                "empty.java", "", "modified");

            assertEquals("empty.java", diff.getFilePath());
            assertEquals("modified", diff.getStatus());
            assertTrue(diff.getHunks().isEmpty());
        }
    }

    // ==================== 综合场景 ====================

    @Nested
    @DisplayName("综合场景")
    class IntegrationScenarios {

        @Test
        @DisplayName("混合场景：一个新增文件、一个修改文件、一个删除文件")
        void shouldHandleMixedFileTypes() {
            String rawDiff = """
                diff --git a/src/Added.java b/src/Added.java
                new file mode 100644
                index 0000000..abc1234
                --- /dev/null
                +++ b/src/Added.java
                @@ -0,0 +1,3 @@
                +package com.example;
                +
                +public class Added {}
                diff --git a/src/Modified.java b/src/Modified.java
                index abc1234..def5678 100644
                --- a/src/Modified.java
                +++ b/src/Modified.java
                @@ -1,3 +1,4 @@
                 package com.example;
                +import java.util.List;
                \s
                 public class Modified {}
                diff --git a/src/Removed.java b/src/Removed.java
                deleted file mode 100644
                index abc1234..0000000
                --- a/src/Removed.java
                +++ /dev/null
                @@ -1,3 +0,0 @@
                -package com.example;
                -
                -public class Removed {}
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            assertEquals(3, results.size());
            assertEquals("added", results.get(0).getStatus());
            assertEquals("modified", results.get(1).getStatus());
            assertEquals("removed", results.get(2).getStatus());

            assertEquals("src/Added.java", results.get(0).getFilePath());
            assertEquals("src/Modified.java", results.get(1).getFilePath());
            assertEquals("src/Removed.java", results.get(2).getFilePath());
        }

        @Test
        @DisplayName("sanitizedContent 应包含完整的去噪后 diff 文本")
        void sanitizedContentShouldContainCleanDiffText() {
            String rawDiff = """
                diff --git a/src/App.java b/src/App.java
                index 111..222 100644
                --- a/src/App.java
                +++ b/src/App.java
                @@ -1,3 +1,4 @@
                 package com.example;
                +import java.util.List;
                \s
                 public class App {}
                """;

            List<SanitizedDiff> results = sanitizer.sanitize(rawDiff);

            SanitizedDiff diff = results.get(0);
            String content = diff.getSanitizedContent();

            assertFalse(content.contains("diff --git"));
            assertFalse(content.contains("index "));
            assertFalse(content.contains("--- "));
            assertFalse(content.contains("+++ "));

            assertTrue(content.contains("@@ -1,3 +1,4 @@"));
            assertTrue(content.contains(" package com.example;"));
            assertTrue(content.contains("+import java.util.List;"));
            assertTrue(content.contains(" public class App {}"));
        }
    }
}
