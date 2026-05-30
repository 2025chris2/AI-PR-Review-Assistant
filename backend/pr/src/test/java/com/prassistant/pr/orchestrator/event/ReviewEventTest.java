package com.prassistant.pr.orchestrator.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReviewEventTest {

    @Nested
    @DisplayName("Builder 构建")
    class Builder {

        @Test
        @DisplayName("应正确构建完整事件")
        void shouldBuildFullEvent() {
            Instant now = Instant.now();
            ReviewEvent event = ReviewEvent.builder()
                    .type(ReviewEventType.L2_FILE_COMPLETE)
                    .taskId("abc12345")
                    .filePath("UserService.java")
                    .message("UserService.java 分析完成")
                    .timestamp(now)
                    .data(Map.of("risk", "LOW"))
                    .build();

            assertEquals(ReviewEventType.L2_FILE_COMPLETE, event.getType());
            assertEquals("abc12345", event.getTaskId());
            assertEquals("UserService.java", event.getFilePath());
            assertEquals(now, event.getTimestamp());
            assertEquals("LOW", event.getData().get("risk"));
        }

        @Test
        @DisplayName("最小构建应使用默认值")
        void shouldBuildMinimalEvent() {
            ReviewEvent event = ReviewEvent.builder()
                    .type(ReviewEventType.TASK_CREATED)
                    .taskId("t1")
                    .build();

            assertEquals(ReviewEventType.TASK_CREATED, event.getType());
            assertNull(event.getFilePath());
            assertNull(event.getMessage());
            assertNull(event.getData());
        }
    }

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("fileEvent 应创建文件级事件")
        void shouldCreateFileEvent() {
            ReviewEvent event = ReviewEvent.fileEvent(
                    ReviewEventType.L2_FILE_START, "t1", "OrderService.java", "开始分析");

            assertEquals(ReviewEventType.L2_FILE_START, event.getType());
            assertEquals("t1", event.getTaskId());
            assertEquals("OrderService.java", event.getFilePath());
            assertEquals("开始分析", event.getMessage());
            assertNotNull(event.getTimestamp());
        }

        @Test
        @DisplayName("stageEvent 应创建阶段事件")
        void shouldCreateStageEvent() {
            ReviewEvent event = ReviewEvent.stageEvent(
                    ReviewEventType.L1_COMPLETE, "t1", "L1 去噪完成");

            assertEquals(ReviewEventType.L1_COMPLETE, event.getType());
            assertEquals("t1", event.getTaskId());
            assertNull(event.getFilePath());
            assertEquals("L1 去噪完成", event.getMessage());
            assertNotNull(event.getTimestamp());
        }
    }

    @Nested
    @DisplayName("ReviewEventType 枚举")
    class EventTypeEnum {

        @Test
        @DisplayName("isFileEvent 应正确判断")
        void shouldIdentifyFileEvents() {
            assertTrue(ReviewEventType.L2_FILE_START.isFileEvent());
            assertTrue(ReviewEventType.L2_FILE_COMPLETE.isFileEvent());
            assertFalse(ReviewEventType.L1_COMPLETE.isFileEvent());
            assertFalse(ReviewEventType.COMPLETED.isFileEvent());
        }

        @Test
        @DisplayName("isTerminal 应正确判断")
        void shouldIdentifyTerminalEvents() {
            assertTrue(ReviewEventType.COMPLETED.isTerminal());
            assertTrue(ReviewEventType.FAILED.isTerminal());
            assertFalse(ReviewEventType.L2_COMPLETE.isTerminal());
            assertFalse(ReviewEventType.ERROR.isTerminal());
        }

        @Test
        @DisplayName("ERROR 不是终态，FAILED 才是")
        void errorIsNotTerminal() {
            assertFalse(ReviewEventType.ERROR.isTerminal());
            assertTrue(ReviewEventType.FAILED.isTerminal());
        }
    }
}
