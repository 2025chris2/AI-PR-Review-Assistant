package com.prassistant.pr.review.analyzer;

import com.prassistant.pr.diff.model.FileChangeType;
import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.review.model.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkPromptBuilderTest {

    @Nested
    @DisplayName("buildWholeFile() — 整文件 Prompt")
    class BuildWholeFile {

        @Test
        @DisplayName("应包含文件路径和代码变更")
        void shouldContainFilePathAndContent() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("src/main/java/UserService.java");
            diff.setStatus(FileChangeType.MODIFIED);
            diff.setSanitizedContent("@@ -10,7 +10,8 @@ public class UserService {\n     public User findById(Long id) {\n-        return userRepository.findOne(id);\n+        return userRepository.findById(id);\n     }\n }");

            String prompt = ChunkPromptBuilder.buildWholeFile(diff);

            assertTrue(prompt.contains("src/main/java/UserService.java"));
            assertTrue(prompt.contains("UserService.java"));
            assertTrue(prompt.contains("MODIFIED"));
            assertTrue(prompt.contains("return userRepository.findOne"));
            assertTrue(prompt.contains("return userRepository.findById"));
            assertFalse(prompt.contains("null"), "不应包含 null 字符串");
        }

        @Test
        @DisplayName("应包含系统角色设定")
        void shouldContainSystemRole() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("test.java");
            diff.setSanitizedContent("+new code");

            String prompt = ChunkPromptBuilder.buildWholeFile(diff);

            assertTrue(prompt.contains(ChunkPromptBuilder.SYSTEM_ROLE));
        }

        @Test
        @DisplayName("应包含 JSON 输出格式要求")
        void shouldContainOutputFormat() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("test.java");
            diff.setSanitizedContent("+new code");

            String prompt = ChunkPromptBuilder.buildWholeFile(diff);

            assertTrue(prompt.contains("\"summary\""));
            assertTrue(prompt.contains("\"risks\""));
            assertTrue(prompt.contains("\"suggestions\""));
            assertTrue(prompt.contains("\"riskLevel\""));
            assertTrue(prompt.contains("HIGH|MEDIUM|LOW"));
        }

        @Test
        @DisplayName("文件路径为 null 时应显示 unknown")
        void shouldShowUnknownWhenFilePathIsNull() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath(null);
            diff.setSanitizedContent("+test");

            String prompt = ChunkPromptBuilder.buildWholeFile(diff);

            assertTrue(prompt.contains("unknown"));
        }

        @Test
        @DisplayName("内容为 null 时应显示空字符串")
        void shouldHandleNullContent() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("test.java");
            diff.setSanitizedContent(null);

            String prompt = ChunkPromptBuilder.buildWholeFile(diff);

            assertTrue(prompt.contains("【代码变更】"));
        }

        @Test
        @DisplayName("不应包含跨块依赖提示")
        void shouldNotContainCrossChunkHint() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("test.java");
            diff.setSanitizedContent("+code");

            String prompt = ChunkPromptBuilder.buildWholeFile(diff);

            assertFalse(prompt.contains("跨块依赖提示"));
        }
    }

    @Nested
    @DisplayName("buildChunk() — 单块 Prompt")
    class BuildChunk {

        @Test
        @DisplayName("应包含全局锚点和代码变更")
        void shouldContainAnchorAndContent() {
            Chunk chunk = Chunk.builder()
                .chunkId("UserService.java#hunk-1")
                .filePath("UserService.java")
                .content("+    public User findById(Long id) {\n+        return userRepository.findById(id);\n+    }")
                .globalAnchor("文件: UserService.java | 类/函数: findById | 第 1/2 块")
                .build();

            String prompt = ChunkPromptBuilder.buildChunk(chunk);

            assertTrue(prompt.contains("UserService.java"));
            assertTrue(prompt.contains("findById"));
            assertTrue(prompt.contains("第 1/2 块"));
            assertTrue(prompt.contains("userRepository.findById"));
        }

        @Test
        @DisplayName("有前序摘要时应包含【前序变更摘要】块")
        void shouldIncludePreviousSummaryWhenPresent() {
            Chunk chunk = Chunk.builder()
                .chunkId("UserService.java#hunk-2")
                .filePath("UserService.java")
                .content("+    public void delete(Long id) {\n+        log.info(\"Deleting: {}\", id);\n+    }")
                .globalAnchor("文件: UserService.java | 类/函数: delete | 第 2/2 块")
                .previousSummary("前序块: 修改了 findById 方法，引入 Optional 返回")
                .build();

            String prompt = ChunkPromptBuilder.buildChunk(chunk);

            assertTrue(prompt.contains("前序块: 修改了 findById 方法"));
            assertTrue(prompt.contains("【前序变更摘要】"));
        }

        @Test
        @DisplayName("无前序摘要时不应包含【前序变更摘要】块")
        void shouldNotIncludePreviousSummaryWhenAbsent() {
            Chunk chunk = Chunk.builder()
                .chunkId("test#hunk-1")
                .filePath("test.java")
                .content("+new code")
                .globalAnchor("第 1/1 块")
                .build();

            String prompt = ChunkPromptBuilder.buildChunk(chunk);

            assertFalse(prompt.contains("【前序变更摘要】"));
        }

        @Test
        @DisplayName("应包含跨块依赖提示（第 6 项）")
        void shouldIncludeCrossChunkHint() {
            Chunk chunk = Chunk.builder()
                .chunkId("test#hunk-1")
                .filePath("test.java")
                .content("+code")
                .globalAnchor("第 1/1 块")
                .build();

            String prompt = ChunkPromptBuilder.buildChunk(chunk);

            assertTrue(prompt.contains("跨块依赖提示"));
            assertTrue(prompt.contains("6."));
        }

        @Test
        @DisplayName("内容为 null 时不应 NPE")
        void shouldHandleNullContent() {
            Chunk chunk = Chunk.builder()
                .chunkId("test#hunk-1")
                .filePath("test.java")
                .content(null)
                .globalAnchor("第 1/1 块")
                .build();

            assertDoesNotThrow(() -> ChunkPromptBuilder.buildChunk(chunk));
            String prompt = ChunkPromptBuilder.buildChunk(chunk);
            assertTrue(prompt.contains("【代码变更】"));
        }

        @Test
        @DisplayName("anchor 为 null 时应显示未知位置")
        void shouldShowUnknownForNullAnchor() {
            Chunk chunk = Chunk.builder()
                .chunkId("test#hunk-1")
                .filePath("test.java")
                .content("+code")
                .globalAnchor(null)
                .build();

            String prompt = ChunkPromptBuilder.buildChunk(chunk);

            assertTrue(prompt.contains("未知位置"));
        }
    }

    @Nested
    @DisplayName("两种模式的差异")
    class ModeDifferences {

        @Test
        @DisplayName("整文件模式不应包含跨块提示，单块模式应包含")
        void wholeFileExcludesCrossChunkHint() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("test.java");
            diff.setSanitizedContent("+code");

            Chunk chunk = Chunk.builder()
                .chunkId("test#hunk-1")
                .filePath("test.java")
                .content("+code")
                .globalAnchor("第 1/1 块")
                .build();

            String wholeFilePrompt = ChunkPromptBuilder.buildWholeFile(diff);
            String chunkPrompt = ChunkPromptBuilder.buildChunk(chunk);

            assertFalse(wholeFilePrompt.contains("跨块依赖提示"));
            assertTrue(chunkPrompt.contains("跨块依赖提示"));
            assertTrue(chunkPrompt.contains("5. 矩阵维度边界检查"));
            assertTrue(chunkPrompt.contains("6. 跨块依赖提示"));
        }

        @Test
        @DisplayName("两种模式都包含系统角色和 JSON 格式要求")
        void bothModesContainSystemRoleAndFormat() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("test.java");
            diff.setSanitizedContent("+code");

            Chunk chunk = Chunk.builder()
                .chunkId("test#hunk-1")
                .filePath("test.java")
                .content("+code")
                .globalAnchor("第 1/1 块")
                .build();

            assertTrue(ChunkPromptBuilder.buildWholeFile(diff).contains(ChunkPromptBuilder.SYSTEM_ROLE));
            assertTrue(ChunkPromptBuilder.buildChunk(chunk).contains(ChunkPromptBuilder.SYSTEM_ROLE));

            assertTrue(ChunkPromptBuilder.buildWholeFile(diff).contains("\"summary\""));
            assertTrue(ChunkPromptBuilder.buildChunk(chunk).contains("\"summary\""));
        }
    }
}
