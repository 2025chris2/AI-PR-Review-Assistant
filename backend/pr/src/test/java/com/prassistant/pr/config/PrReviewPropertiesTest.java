package com.prassistant.pr.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrReviewPropertiesTest {

    @Nested
    @DisplayName("默认值")
    class Defaults {

        @Test
        @DisplayName("L2 超时默认为 60 秒")
        void l2TimeoutDefaultsTo60() {
            PrReviewProperties props = new PrReviewProperties();
            assertEquals(60, props.getL2TimeoutSeconds());
        }

        @Test
        @DisplayName("L3 超时默认为 30 秒")
        void l3TimeoutDefaultsTo30() {
            PrReviewProperties props = new PrReviewProperties();
            assertEquals(30, props.getL3TimeoutSeconds());
        }

        @Test
        @DisplayName("SSE 超时默认为 180 秒")
        void sseTimeoutDefaultsTo180s() {
            PrReviewProperties props = new PrReviewProperties();
            assertEquals(180_000L, props.getSseTimeoutMs());
        }

        @Test
        @DisplayName("最大并发文件数默认为 5")
        void maxConcurrentFilesDefaultsTo5() {
            PrReviewProperties props = new PrReviewProperties();
            assertEquals(5, props.getMaxConcurrentFiles());
        }
    }

    @Nested
    @DisplayName("Setter 覆盖")
    class Setters {

        @Test
        @DisplayName("应正确覆盖各属性")
        void shouldOverrideAllProperties() {
            PrReviewProperties props = new PrReviewProperties();
            props.setL2TimeoutSeconds(120);
            props.setL3TimeoutSeconds(60);
            props.setSseTimeoutMs(300_000L);
            props.setMaxConcurrentFiles(10);

            assertEquals(120, props.getL2TimeoutSeconds());
            assertEquals(60, props.getL3TimeoutSeconds());
            assertEquals(300_000L, props.getSseTimeoutMs());
            assertEquals(10, props.getMaxConcurrentFiles());
        }
    }
}
