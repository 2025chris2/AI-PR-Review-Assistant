package com.prassistant.pr.review.util;

import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenEstimatorTest {

    @Nested
    @DisplayName("count(String) — 文本 Token 计数")
    class CountString {

        @Test
        @DisplayName("null 输入应返回 0")
        void shouldReturnZeroForNull() {
            assertEquals(0, TokenEstimator.count((String) null));
        }

        @Test
        @DisplayName("空字符串应返回 0")
        void shouldReturnZeroForEmptyString() {
            assertEquals(0, TokenEstimator.count(""));
        }

        @Test
        @DisplayName("简单文本的 Token 数应大于 0")
        void shouldReturnPositiveCountForText() {
            int count = TokenEstimator.count("Hello, world!");
            assertTrue(count > 0, "简单文本的 Token 数应大于 0");
        }

        @Test
        @DisplayName("长文本的 Token 数应显著大于短文本")
        void shouldCountLongTextMoreThanShortText() {
            String shortText = "Hello, world!";
            String longText = shortText.repeat(100);
            int shortCount = TokenEstimator.count(shortText);
            int longCount = TokenEstimator.count(longText);
            assertTrue(longCount > shortCount * 50, "长文本 Token 数应显著大于短文本");
        }

        @Test
        @DisplayName("Java 代码片段的 Token 数应正确计算")
        void shouldCountJavaCodeTokens() {
            String code = """
                public class UserService {
                    public User findById(Long id) {
                        return userRepository.findById(id)
                            .orElseThrow(() -> new NotFoundException("User not found"));
                    }
                }
                """;
            int count = TokenEstimator.count(code);
            assertTrue(count > 10, "代码片段的 Token 数应大于 10");
            assertTrue(count < 200, "代码片段的 Token 数应小于 200");
        }

        @Test
        @DisplayName("大量重复文本的 Token 数应远大于短文本")
        void shouldScaleLinearlyWithContent() {
            String word = "token ";
            int base = TokenEstimator.count(word);
            int multiplied = TokenEstimator.count(word.repeat(100));
            assertTrue(multiplied >= base * 50, "重复 100 次的 Token 数应至少是单次的 50 倍");
        }
    }

    @Nested
    @DisplayName("count(String, EncodingType) — 指定编码的 Token 计数")
    class CountWithEncodingType {

        @Test
        @DisplayName("cl100k_base 和 p50k_base 对相同文本应返回不同结果")
        void shouldReturnDifferentCountsForDifferentEncodings() {
            String text = "Hello, world! This is a test with some Unicode: 你好";
            int cl100k = TokenEstimator.count(text, EncodingType.CL100K_BASE);
            int p50k = TokenEstimator.count(text, EncodingType.P50K_BASE);
            // 两种编码对 Unicode 的处理不同，结果应不同
            assertNotEquals(cl100k, p50k);
        }

        @Test
        @DisplayName("null 文本指定编码应返回 0")
        void shouldReturnZeroForNullWithEncoding() {
            assertEquals(0, TokenEstimator.count(null, EncodingType.CL100K_BASE));
        }
    }

    @Nested
    @DisplayName("exceedsThreshold() — 阈值判断")
    class ExceedsThreshold {

        @Test
        @DisplayName("长文本超过默认阈值应返回 true")
        void shouldReturnTrueForLongTextExceedingDefaultThreshold() {
            // 8000+ tokens 需要足够的文本
            String longText = "token ".repeat(10000);
            assertTrue(TokenEstimator.exceedsThreshold(longText));
        }

        @Test
        @DisplayName("短文本未超过默认阈值应返回 false")
        void shouldReturnFalseForShortText() {
            assertFalse(TokenEstimator.exceedsThreshold("Hello, world!"));
        }

        @Test
        @DisplayName("空字符串未超过任何阈值")
        void shouldNotExceedThresholdForEmptyString() {
            assertFalse(TokenEstimator.exceedsThreshold("", 1));
        }

        @Test
        @DisplayName("指定较低阈值时短文本也应触发")
        void shouldRespectCustomThreshold() {
            String text = "Hello, world! This is a test.";
            assertTrue(TokenEstimator.exceedsThreshold(text, 1));
            assertFalse(TokenEstimator.exceedsThreshold(text, 1000));
        }
    }

    @Nested
    @DisplayName("常量校验")
    class Constants {

        @Test
        @DisplayName("默认分块阈值应为 8000")
        void defaultChunkThresholdShouldBe8000() {
            assertEquals(8000, TokenEstimator.DEFAULT_CHUNK_THRESHOLD);
        }
    }
}
