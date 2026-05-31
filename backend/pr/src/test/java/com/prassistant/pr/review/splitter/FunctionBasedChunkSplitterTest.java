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

class FunctionBasedChunkSplitterTest {

    private FunctionBasedChunkSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new FunctionBasedChunkSplitter(new HunkBasedChunkSplitter());
    }

    @Nested
    @DisplayName("isMethodSignature() — 方法签名识别")
    class IsMethodSignature {

        @Test
        @DisplayName("public void 方法应被识别")
        void shouldRecognizePublicVoidMethod() {
            assertTrue(splitter.isMethodSignature("public void doSomething() {"));
            assertTrue(splitter.isMethodSignature("    public void doSomething() {"));
            assertTrue(splitter.isMethodSignature("+    public void doSomething() {"));
            assertTrue(splitter.isMethodSignature("-    public void doSomething() {"));
        }

        @Test
        @DisplayName("private String 方法应被识别")
        void shouldRecognizePrivateStringMethod() {
            assertTrue(splitter.isMethodSignature("private String getName() {"));
            assertTrue(splitter.isMethodSignature("    private String getName(Long id) {"));
        }

        @Test
        @DisplayName("protected 方法应被识别")
        void shouldRecognizeProtectedMethod() {
            assertTrue(splitter.isMethodSignature("protected void onInit() {"));
        }

        @Test
        @DisplayName("class/interface 定义不应被识别为方法")
        void shouldNotRecognizeClassOrInterface() {
            assertFalse(splitter.isMethodSignature("public class UserService {"));
            assertFalse(splitter.isMethodSignature("public interface UserRepository {"));
        }

        @Test
        @DisplayName("import/注解/注释不应被识别为方法")
        void shouldNotRecognizeImportsAnnotationsOrComments() {
            assertFalse(splitter.isMethodSignature("import java.util.List;"));
            assertFalse(splitter.isMethodSignature("@Override"));
            assertFalse(splitter.isMethodSignature("// TODO: implement"));
        }

        @Test
        @DisplayName("null 行应返回 false")
        void shouldReturnFalseForNull() {
            assertFalse(splitter.isMethodSignature(null));
        }
    }

    @Nested
    @DisplayName("refine() — 超长 Chunk 二次切分")
    class Refine {

        @Test
        @DisplayName("单个方法 → 不拆分")
        void shouldNotSplitSingleMethod() {
            Chunk chunk = Chunk.builder()
                .chunkId("Test.java#hunk-1")
                .filePath("Test.java")
                .content("public void doSomething() {\n    System.out.println(\"ok\");\n}")
                .tokenCount(100)
                .needsFurtherSplit(true)
                .build();

            List<Chunk> result = splitter.refine(chunk);
            assertEquals(1, result.size());
            assertFalse(result.get(0).isNeedsFurtherSplit());
        }

        @Test
        @DisplayName("多个方法 → 按方法边界切分")
        void shouldSplitMultipleMethods() {
            String content = ""
                + "public void methodA() {\n"
                + "    doA();\n"
                + "}\n"
                + "private int methodB() {\n"
                + "    return 42;\n"
                + "}\n"
                + "protected String methodC() {\n"
                + "    return \"ok\";\n"
                + "}";

            Chunk chunk = Chunk.builder()
                .chunkId("MultiMethod.java#hunk-1")
                .filePath("MultiMethod.java")
                .content(content)
                .tokenCount(5000)
                .previousSummary("前序块已处理")
                .needsFurtherSplit(true)
                .build();

            List<Chunk> result = splitter.refine(chunk);
            assertEquals(3, result.size(), "3 个方法应拆为 3 块");

            assertEquals("MultiMethod.java#hunk-1-fn-1", result.get(0).getChunkId());
            assertEquals("MultiMethod.java#hunk-1-fn-2", result.get(1).getChunkId());
            assertEquals("MultiMethod.java#hunk-1-fn-3", result.get(2).getChunkId());

            // 前序摘要链
            assertEquals("前序块已处理", result.get(0).getPreviousSummary());
            assertNotNull(result.get(1).getPreviousSummary());
            assertTrue(result.get(1).getPreviousSummary().contains("前序块已完成"));
        }

        @Test
        @DisplayName("空内容 → 空列表")
        void shouldReturnEmptyForBlankContent() {
            Chunk chunk = Chunk.builder()
                .chunkId("Empty.java#hunk-1")
                .filePath("Empty.java")
                .content("")
                .build();

            assertTrue(splitter.refine(chunk).isEmpty());
        }

        @Test
        @DisplayName("null 内容 → 空列表")
        void shouldReturnEmptyForNullContent() {
            Chunk chunk = Chunk.builder()
                .chunkId("Null.java#hunk-1")
                .filePath("Null.java")
                .content(null)
                .build();

            assertTrue(splitter.refine(chunk).isEmpty());
        }

        @Test
        @DisplayName("未标记需拆分的 Chunk 应返回单个不变")
        void shouldReturnSingleChunkIfNotNeedingSplit() {
            Chunk chunk = Chunk.builder()
                .chunkId("Simple.java#hunk-1")
                .filePath("Simple.java")
                .content("public void foo() {}")
                .needsFurtherSplit(false)
                .build();

            List<Chunk> result = splitter.refine(chunk);
            assertEquals(1, result.size());
            assertFalse(result.get(0).isNeedsFurtherSplit());
        }
    }

    @Nested
    @DisplayName("split(SanitizedDiff) — 完整路径")
    class SplitDiff {

        @Test
        @DisplayName("单个 Hunk 单个方法 → 1 个 Chunk")
        void shouldReturnOneChunkForSingleMethod() {
            SanitizedDiff diff = new SanitizedDiff();
            diff.setFilePath("TestService.java");
            DiffHunk hunk = new DiffHunk();
            hunk.setOldStartLine(1);
            hunk.setOldLineCount(3);
            hunk.setNewStartLine(1);
            hunk.setNewLineCount(3);
            hunk.setSectionHeader("class TestService {");
            hunk.getLines().add(" public class TestService {");
            hunk.getLines().add("     public void doIt() { }");
            hunk.getLines().add(" }");
            diff.getHunks().add(hunk);

            List<Chunk> chunks = splitter.split(diff);
            assertEquals(1, chunks.size());
            assertEquals("TestService.java#hunk-1", chunks.get(0).getChunkId());
        }
    }

    @Nested
    @DisplayName("refine() 多方法拆分")
    class RefineMultipleMethods {

        @Test
        @DisplayName("多个方法 → 按方法边界切分")
        void shouldSplitByMethodBoundaries() {
            String content = " public class Service {\n"
                + "     public void methodA() {\n"
                + "         doA();\n"
                + "     }\n"
                + "     private int methodB() {\n"
                + "         return 42;\n"
                + "     }\n"
                + " }";

            Chunk chunk = Chunk.builder()
                .chunkId("MultiMethod.java#hunk-1")
                .filePath("MultiMethod.java")
                .content(content)
                .tokenCount(5000)
                .needsFurtherSplit(true)
                .build();

            List<Chunk> result = splitter.refine(chunk);
            assertTrue(result.size() >= 2, "多个方法应拆为至少 2 块");
            assertTrue(result.get(0).getChunkId().contains("fn-1"));
            assertTrue(result.get(1).getChunkId().contains("fn-2"));
        }
    }

    @Nested
    @DisplayName("findMethodBoundaries() — 切分点识别")
    class FindMethodBoundaries {

        @Test
        @DisplayName("应正确识别方法切分点（包含文件开头）")
        void shouldFindMethodSplitPoints() {
            String[] lines = {
                " public class Service {",
                "     public void methodA() {",
                "         doA();",
                "     }",
                "     private int methodB() {",
                "         return 42;",
                "     }",
                " }"
            };
            List<Integer> boundaries = splitter.findMethodBoundaries(lines);
            // 0: 开头边界, 1: methodA, 4: methodB
            assertEquals(3, boundaries.size(), "应包含文件开头 + 2 个方法边界");
            assertEquals(0, (int) boundaries.get(0));
            assertEquals(1, (int) boundaries.get(1));
            assertEquals(4, (int) boundaries.get(2));
        }
    }
}
