package com.prassistant.pr.review.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkTest {

    @Nested
    @DisplayName("Builder 模式 — 正常构建")
    class Builder {

        @Test
        @DisplayName("应正确设置所有字段")
        void shouldSetAllFieldsViaBuilder() {
            Chunk chunk = Chunk.builder()
                .chunkId("UserService.java#hunk-1")
                .filePath("src/main/java/com/example/UserService.java")
                .content("+    public User findById(Long id) {\n+        return userRepository.findById(id);\n+    }")
                .globalAnchor("类: UserService / 方法: findById / 第 1/3 块")
                .previousSummary("前序块: 新增了 UserRepository 接口")
                .tokenCount(85)
                .hunkRange("-45,7 +45,9")
                .needsFurtherSplit(false)
                .build();

            assertEquals("UserService.java#hunk-1", chunk.getChunkId());
            assertEquals("src/main/java/com/example/UserService.java", chunk.getFilePath());
            assertTrue(chunk.getContent().contains("findById"));
            assertEquals("类: UserService / 方法: findById / 第 1/3 块", chunk.getGlobalAnchor());
            assertEquals("前序块: 新增了 UserRepository 接口", chunk.getPreviousSummary());
            assertEquals(85, chunk.getTokenCount());
            assertEquals("-45,7 +45,9", chunk.getHunkRange());
            assertFalse(chunk.isNeedsFurtherSplit());
        }

        @Test
        @DisplayName("未设置的字段应有默认值")
        void shouldHaveDefaultsForUnsetFields() {
            Chunk chunk = Chunk.builder()
                .chunkId("test#hunk-1")
                .filePath("test.java")
                .build();

            assertEquals("test#hunk-1", chunk.getChunkId());
            assertEquals("test.java", chunk.getFilePath());
            assertNull(chunk.getContent());
            assertNull(chunk.getGlobalAnchor());
            assertNull(chunk.getPreviousSummary());
            assertEquals(0, chunk.getTokenCount());
            assertNull(chunk.getHunkRange());
            assertFalse(chunk.isNeedsFurtherSplit());
        }
    }

    @Nested
    @DisplayName("无参构造 + Setter — Lombok @Data")
    class NoArgsConstructorAndSetters {

        @Test
        @DisplayName("无参构造后通过 setter 设置应正常")
        void shouldWorkWithNoArgsConstructorAndSetters() {
            Chunk chunk = new Chunk();
            chunk.setChunkId("OrderService.java#hunk-2");
            chunk.setFilePath("src/OrderService.java");
            chunk.setContent("-old code\n+new code");
            chunk.setGlobalAnchor("类: OrderService / 第 2/2 块");
            chunk.setPreviousSummary("前序块: 重构了 createOrder 方法");
            chunk.setTokenCount(42);
            chunk.setHunkRange("-10,5 +10,6");
            chunk.setNeedsFurtherSplit(true);

            assertEquals("OrderService.java#hunk-2", chunk.getChunkId());
            assertEquals("src/OrderService.java", chunk.getFilePath());
            assertEquals("-old code\n+new code", chunk.getContent());
            assertEquals("类: OrderService / 第 2/2 块", chunk.getGlobalAnchor());
            assertEquals("前序块: 重构了 createOrder 方法", chunk.getPreviousSummary());
            assertEquals(42, chunk.getTokenCount());
            assertEquals("-10,5 +10,6", chunk.getHunkRange());
            assertTrue(chunk.isNeedsFurtherSplit());
        }
    }

    @Nested
    @DisplayName("全参构造 — @AllArgsConstructor")
    class AllArgsConstructor {

        @Test
        @DisplayName("全参构造应正确初始化")
        void shouldInitializeAllFields() {
            Chunk chunk = new Chunk(
                "ProductMapper.xml#hunk-1",
                "src/resources/ProductMapper.xml",
                "+    <select id=\"findAll\" resultType=\"Product\">\n+        SELECT * FROM products\n+    </select>",
                "文件: ProductMapper.xml / 第 1 块",
                null,
                60,
                "-1,1 +1,6",
                false
            );

            assertEquals("ProductMapper.xml#hunk-1", chunk.getChunkId());
            assertEquals(60, chunk.getTokenCount());
            assertNull(chunk.getPreviousSummary());
        }
    }

    @Nested
    @DisplayName("equals / hashCode / toString — Lombok @Data")
    class EqualsHashCodeToString {

        @Test
        @DisplayName("相同字段的两个实例应 equal")
        void shouldBeEqualWhenSameFields() {
            Chunk chunk1 = Chunk.builder()
                .chunkId("test#hunk-1")
                .filePath("test.java")
                .content("code")
                .tokenCount(10)
                .build();

            Chunk chunk2 = Chunk.builder()
                .chunkId("test#hunk-1")
                .filePath("test.java")
                .content("code")
                .tokenCount(10)
                .build();

            assertEquals(chunk1, chunk2);
            assertEquals(chunk1.hashCode(), chunk2.hashCode());
        }

        @Test
        @DisplayName("不同 chunkId 的两个实例应不相等")
        void shouldNotBeEqualWhenDifferentChunkId() {
            Chunk chunk1 = Chunk.builder().chunkId("a#hunk-1").filePath("test.java").build();
            Chunk chunk2 = Chunk.builder().chunkId("b#hunk-1").filePath("test.java").build();

            assertNotEquals(chunk1, chunk2);
        }

        @Test
        @DisplayName("toString 应包含关键字段")
        void toStringShouldContainKeyFields() {
            Chunk chunk = Chunk.builder()
                .chunkId("test#hunk-1")
                .filePath("test.java")
                .build();

            String str = chunk.toString();
            assertTrue(str.contains("test#hunk-1"));
            assertTrue(str.contains("test.java"));
            assertTrue(str.contains("Chunk"));
        }
    }
}
