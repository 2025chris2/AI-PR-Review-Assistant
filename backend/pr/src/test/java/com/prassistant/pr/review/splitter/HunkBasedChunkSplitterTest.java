package com.prassistant.pr.review.splitter;

import com.prassistant.pr.diff.model.DiffHunk;
import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.review.model.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HunkBasedChunkSplitterTest {

    private HunkBasedChunkSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new HunkBasedChunkSplitter();
    }

    private SanitizedDiff createDiffWithHunks(int hunkCount) {
        SanitizedDiff diff = new SanitizedDiff();
        diff.setFilePath("src/main/java/UserService.java");
        for (int i = 0; i < hunkCount; i++) {
            DiffHunk hunk = new DiffHunk();
            hunk.setOldStartLine(i * 10 + 1);
            hunk.setOldLineCount(5);
            hunk.setNewStartLine(i * 10 + 1);
            hunk.setNewLineCount(6);
            hunk.setSectionHeader("public class UserService {");
            hunk.getLines().add(" public class UserService {");
            hunk.getLines().add("     public void method" + i + "() {");
            hunk.getLines().add("+        System.out.println(\"added\");");
            hunk.getLines().add("-        System.out.println(\"removed\");");
            hunk.getLines().add("     }");
            hunk.getLines().add(" }");
            diff.getHunks().add(hunk);
        }
        return diff;
    }

    @Nested
    @DisplayName("基本切分")
    class BasicSplitting {

        @Test
        @DisplayName("单个 Hunk → 1 个 Chunk")
        void shouldMapSingleHunkToSingleChunk() {
            SanitizedDiff diff = createDiffWithHunks(1);
            List<Chunk> chunks = splitter.split(diff);

            assertEquals(1, chunks.size());
            Chunk chunk = chunks.get(0);
            assertEquals("src/main/java/UserService.java#hunk-1", chunk.getChunkId());
            assertEquals("src/main/java/UserService.java", chunk.getFilePath());
            assertTrue(chunk.getTokenCount() > 0);
            assertFalse(chunk.isNeedsFurtherSplit());
        }

        @Test
        @DisplayName("多个 Hunk → 多个 Chunk")
        void shouldMapMultipleHunksToMultipleChunks() {
            SanitizedDiff diff = createDiffWithHunks(3);
            List<Chunk> chunks = splitter.split(diff);

            assertEquals(3, chunks.size());
            assertEquals("src/main/java/UserService.java#hunk-1", chunks.get(0).getChunkId());
            assertEquals("src/main/java/UserService.java#hunk-2", chunks.get(1).getChunkId());
            assertEquals("src/main/java/UserService.java#hunk-3", chunks.get(2).getChunkId());
        }

        @Test
        @DisplayName("空 Hunk 列表 → 空列表")
        void shouldReturnEmptyListForNoHunks() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("empty.java");
            List<Chunk> chunks = splitter.split(diff);
            assertTrue(chunks.isEmpty());
        }

        @Test
        @DisplayName("null Hunk 列表 → 空列表")
        void shouldReturnEmptyListForNullHunks() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("test.java");
            diff.setHunks(null);
            List<Chunk> chunks = splitter.split(diff);
            assertTrue(chunks.isEmpty());
        }
    }

    @Nested
    @DisplayName("全局锚点")
    class GlobalAnchor {

        @Test
        @DisplayName("应包含文件路径、section header、块序号")
        void shouldContainFilePathSectionAndIndex() {
            SanitizedDiff diff = createDiffWithHunks(2);
            List<Chunk> chunks = splitter.split(diff);

            assertEquals("文件: src/main/java/UserService.java | 类/函数: public class UserService { | 第 1/2 块",
                chunks.get(0).getGlobalAnchor());
            assertEquals("文件: src/main/java/UserService.java | 类/函数: public class UserService { | 第 2/2 块",
                chunks.get(1).getGlobalAnchor());
        }
    }

    @Nested
    @DisplayName("前序摘要链")
    class PreviousSummaryChain {

        @Test
        @DisplayName("前序块应有前序摘要，首块应为 null")
        void shouldChainPreviousSummaries() {
            SanitizedDiff diff = createDiffWithHunks(2);
            List<Chunk> chunks = splitter.split(diff);

            assertNull(chunks.get(0).getPreviousSummary());
            assertNotNull(chunks.get(1).getPreviousSummary());
            assertTrue(chunks.get(1).getPreviousSummary().contains("hunk-1"));
        }

        @Test
        @DisplayName("前序摘要应包含 +N/-N 变更统计")
        void previousSummaryShouldContainChangeStats() {
            SanitizedDiff diff = createDiffWithHunks(2);
            List<Chunk> chunks = splitter.split(diff);

            String summary = chunks.get(1).getPreviousSummary();
            assertTrue(summary.contains("+1") || summary.contains("-1"),
                "前序摘要应包含变更行统计");
        }
    }

    @Nested
    @DisplayName("行号范围")
    class HunkRange {

        @Test
        @DisplayName("应正确格式化行号范围")
        void shouldFormatHunkRange() {
            SanitizedDiff diff = createDiffWithHunks(1);
            List<Chunk> chunks = splitter.split(diff);

            assertEquals("-1,5 +1,6", chunks.get(0).getHunkRange());
        }
    }

    @Nested
    @DisplayName("Hunk 内容构建")
    class ContentBuilding {

        @Test
        @DisplayName("content 应包含 @@ 头和代码行")
        void contentShouldContainHeaderAndLines() {
            SanitizedDiff diff = createDiffWithHunks(1);
            List<Chunk> chunks = splitter.split(diff);

            String content = chunks.get(0).getContent();
            assertTrue(content.startsWith("@@"));
            assertFalse(content.contains("null"), "不应包含 null 字符串");
            assertTrue(content.contains("UserService"));
        }
    }

    @Nested
    @DisplayName("needsFurtherSplit 标记")
    class NeedsFurtherSplit {

        @Test
        @DisplayName("小 Hunk 不应标记为需进一步拆分")
        void shouldNotMarkSmallHunkForFurtherSplit() {
            SanitizedDiff diff = createDiffWithHunks(1);
            List<Chunk> chunks = splitter.split(diff);
            assertFalse(chunks.get(0).isNeedsFurtherSplit());
        }

        @Test
        @DisplayName("大 Hunk（超过 8000 token）应标记为需进一步拆分")
        void shouldMarkLargeHunkForFurtherSplit() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("LargeFile.java");
            DiffHunk largeHunk = new DiffHunk();
            largeHunk.setOldStartLine(1);
            largeHunk.setOldLineCount(10000);
            largeHunk.setNewStartLine(1);
            largeHunk.setNewLineCount(10000);
            // 填充足够多的行以超过 8000 阈值
            for (int i = 0; i < 3000; i++) {
                largeHunk.getLines().add("+    public void method" + i + "() { /* long body */ }");
            }
            diff.getHunks().add(largeHunk);

            List<Chunk> chunks = splitter.split(diff);
            assertEquals(1, chunks.size());
            assertTrue(chunks.get(0).isNeedsFurtherSplit(),
                "超过阈值的大 Hunk 应标记 needsFurtherSplit");
        }
    }
}
