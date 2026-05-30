package com.prassistant.pr.review.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkReviewResultTest {

    @Nested
    @DisplayName("Builder 模式 — 正常构建")
    class Builder {

        @Test
        @DisplayName("应正确设置所有字段")
        void shouldSetAllFieldsViaBuilder() {
            ChunkReviewResult result = ChunkReviewResult.builder()
                .chunkId("UserService.java#hunk-1")
                .summary("修改了 findById 方法，将 findOne 替换为 findById + orElseThrow")
                .risks(List.of(
                    new ChunkReviewResult.RiskItem("NullPointer", 48, "orElseThrow 可能抛出 NotFoundException 未捕获"),
                    new ChunkReviewResult.RiskItem("Concurrency", 50, "没有加锁，并发访问可能导致数据不一致")
                ))
                .suggestions(List.of(
                    new ChunkReviewResult.SuggestionItem(1, "建议在方法签名上添加 @Transactional 注解"),
                    new ChunkReviewResult.SuggestionItem(2, "建议为异常添加全局异常处理器")
                ))
                .crossChunkHints("新增了 Optional 导入和 NotFoundException 异常类")
                .riskLevel(ChunkReviewResult.RiskLevel.MEDIUM)
                .build();

            assertEquals("UserService.java#hunk-1", result.getChunkId());
            assertTrue(result.getSummary().contains("findById"));
            assertEquals(2, result.getRisks().size());
            assertEquals(2, result.getSuggestions().size());
            assertTrue(result.getCrossChunkHints().contains("Optional"));
            assertEquals(ChunkReviewResult.RiskLevel.MEDIUM, result.getRiskLevel());
        }

        @Test
        @DisplayName("未设置的字段应有默认值")
        void shouldHaveDefaultsForUnsetFields() {
            ChunkReviewResult result = ChunkReviewResult.builder()
                .chunkId("test#hunk-1")
                .summary("test")
                .build();

            assertEquals("test#hunk-1", result.getChunkId());
            assertEquals("test", result.getSummary());
            assertNull(result.getRisks());
            assertNull(result.getSuggestions());
            assertNull(result.getCrossChunkHints());
            assertNull(result.getRiskLevel());
        }
    }

    @Nested
    @DisplayName("RiskItem record — 风险点")
    class RiskItemRecord {

        @Test
        @DisplayName("应正确创建风险点记录")
        void shouldCreateRiskItem() {
            var risk = new ChunkReviewResult.RiskItem("NullPointer", 42, "potential NPE");
            assertEquals("NullPointer", risk.type());
            assertEquals(42, risk.line());
            assertEquals("potential NPE", risk.description());
        }

        @Test
        @DisplayName("相同字段的 RiskItem 应 equal")
        void shouldBeEqual() {
            var r1 = new ChunkReviewResult.RiskItem("NullPointer", 42, "potential NPE");
            var r2 = new ChunkReviewResult.RiskItem("NullPointer", 42, "potential NPE");
            assertEquals(r1, r2);
        }

        @Test
        @DisplayName("不同字段的 RiskItem 应不相等")
        void shouldNotBeEqual() {
            var r1 = new ChunkReviewResult.RiskItem("NullPointer", 42, "potential NPE");
            var r2 = new ChunkReviewResult.RiskItem("SQL", 42, "potential NPE");
            assertNotEquals(r1, r2);
        }
    }

    @Nested
    @DisplayName("SuggestionItem record — Review 建议")
    class SuggestionItemRecord {

        @Test
        @DisplayName("应正确创建建议记录")
        void shouldCreateSuggestionItem() {
            var suggestion = new ChunkReviewResult.SuggestionItem(1, "add @Transactional");
            assertEquals(1, suggestion.priority());
            assertEquals("add @Transactional", suggestion.description());
        }

        @Test
        @DisplayName("优先级高的建议应排在前面")
        void higherPriorityShouldComeFirst() {
            var s1 = new ChunkReviewResult.SuggestionItem(1, "high priority");
            var s2 = new ChunkReviewResult.SuggestionItem(2, "medium priority");
            var s3 = new ChunkReviewResult.SuggestionItem(3, "low priority");
            assertTrue(s1.priority() < s2.priority());
            assertTrue(s2.priority() < s3.priority());
        }
    }

    @Nested
    @DisplayName("RiskLevel 枚举")
    class RiskLevelEnum {

        @Test
        @DisplayName("应按严重程度包含三个等级")
        void shouldContainThreeLevels() {
            assertEquals(3, ChunkReviewResult.RiskLevel.values().length);
            assertTrue(ChunkReviewResult.RiskLevel.valueOf("HIGH").ordinal() <
                       ChunkReviewResult.RiskLevel.valueOf("MEDIUM").ordinal());
            assertTrue(ChunkReviewResult.RiskLevel.valueOf("MEDIUM").ordinal() <
                       ChunkReviewResult.RiskLevel.valueOf("LOW").ordinal());
        }
    }

    @Nested
    @DisplayName("equals / hashCode / toString")
    class EqualsHashCodeToString {

        @Test
        @DisplayName("相同字段应 equal")
        void shouldBeEqual() {
            var r1 = ChunkReviewResult.builder()
                .chunkId("test#hunk-1")
                .summary("test")
                .riskLevel(ChunkReviewResult.RiskLevel.LOW)
                .build();
            var r2 = ChunkReviewResult.builder()
                .chunkId("test#hunk-1")
                .summary("test")
                .riskLevel(ChunkReviewResult.RiskLevel.LOW)
                .build();
            assertEquals(r1, r2);
        }

        @Test
        @DisplayName("toString 应包含关键字段")
        void toStringShouldContainKeyFields() {
            var result = ChunkReviewResult.builder()
                .chunkId("test#hunk-1")
                .summary("changed something")
                .riskLevel(ChunkReviewResult.RiskLevel.HIGH)
                .build();
            String str = result.toString();
            assertTrue(str.contains("test#hunk-1"));
            assertTrue(str.contains("changed something"));
            assertTrue(str.contains("HIGH"));
        }
    }
}
