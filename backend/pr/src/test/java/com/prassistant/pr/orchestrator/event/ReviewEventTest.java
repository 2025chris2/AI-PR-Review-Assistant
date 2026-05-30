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
                    .type(ReviewEventType.TASK_STARTED)
                    .taskId("t1")
                    .build();

            assertEquals(ReviewEventType.TASK_STARTED, event.getType());
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
        @DisplayName("fileEvent 带 data 应创建含额外数据的文件级事件")
        void shouldCreateFileEventWithData() {
            Map<String, Object> data = Map.of("chunkCount", 3);
            ReviewEvent event = ReviewEvent.fileEvent(
                    ReviewEventType.L2_FILE_START, "t1", "OrderService.java", "开始分析", data);

            assertEquals(ReviewEventType.L2_FILE_START, event.getType());
            assertEquals(3, event.getData().get("chunkCount"));
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

        @Test
        @DisplayName("stageEvent 带 data 应创建含额外数据的阶段事件")
        void shouldCreateStageEventWithData() {
            Map<String, Object> data = Map.of("fileCount", 5, "tokenSaved", 0.45);
            ReviewEvent event = ReviewEvent.stageEvent(
                    ReviewEventType.L1_COMPLETE, "t1", "L1 去噪完成", data);

            assertEquals(5, event.getData().get("fileCount"));
            assertEquals(0.45, event.getData().get("tokenSaved"));
        }

        @Test
        @DisplayName("errorEvent 可恢复错误应使用 ERROR 类型")
        void shouldCreateRecoverableErrorEvent() {
            ReviewEvent event = ReviewEvent.errorEvent("t1", "L2", "文件分析失败", true);

            assertEquals(ReviewEventType.ERROR, event.getType());
            assertEquals("L2", event.getData().get("stage"));
            assertEquals("文件分析失败", event.getData().get("message"));
            assertEquals(true, event.getData().get("recoverable"));
        }

        @Test
        @DisplayName("errorEvent 不可恢复错误应使用 FAILED 类型")
        void shouldCreateFatalErrorEvent() {
            ReviewEvent event = ReviewEvent.errorEvent("t1", "L1", "解析超时", false);

            assertEquals(ReviewEventType.FAILED, event.getType());
            assertEquals(false, event.getData().get("recoverable"));
        }
    }

    @Nested
    @DisplayName("ReviewEventType 枚举")
    class EventTypeEnum {

        @Test
        @DisplayName("getSseName 应返回点号格式的事件名")
        void shouldReturnDotSeparatedSseName() {
            assertEquals("task.started", ReviewEventType.TASK_STARTED.getSseName());
            assertEquals("l1.complete", ReviewEventType.L1_COMPLETE.getSseName());
            assertEquals("l2.file.start", ReviewEventType.L2_FILE_START.getSseName());
            assertEquals("l2.file.chunk", ReviewEventType.L2_FILE_CHUNK.getSseName());
            assertEquals("l2.file.complete", ReviewEventType.L2_FILE_COMPLETE.getSseName());
            assertEquals("l2.complete", ReviewEventType.L2_COMPLETE.getSseName());
            assertEquals("l3.start", ReviewEventType.L3_START.getSseName());
            assertEquals("l3.complete", ReviewEventType.L3_COMPLETE.getSseName());
            assertEquals("result", ReviewEventType.RESULT.getSseName());
            assertEquals("completed", ReviewEventType.COMPLETED.getSseName());
            assertEquals("error", ReviewEventType.ERROR.getSseName());
            assertEquals("error", ReviewEventType.FAILED.getSseName());
        }

        @Test
        @DisplayName("isFileEvent 应正确判断")
        void shouldIdentifyFileEvents() {
            assertTrue(ReviewEventType.L2_FILE_START.isFileEvent());
            assertTrue(ReviewEventType.L2_FILE_CHUNK.isFileEvent());
            assertTrue(ReviewEventType.L2_FILE_COMPLETE.isFileEvent());
            assertFalse(ReviewEventType.L1_COMPLETE.isFileEvent());
            assertFalse(ReviewEventType.RESULT.isFileEvent());
        }

        @Test
        @DisplayName("isTerminal 应正确判断")
        void shouldIdentifyTerminalEvents() {
            assertTrue(ReviewEventType.COMPLETED.isTerminal());
            assertTrue(ReviewEventType.FAILED.isTerminal());
            assertTrue(ReviewEventType.RESULT.isTerminal());
            assertFalse(ReviewEventType.L2_COMPLETE.isTerminal());
            assertFalse(ReviewEventType.ERROR.isTerminal());
        }

        @Test
        @DisplayName("ERROR 不是终态，FAILED 才是")
        void errorIsNotTerminal() {
            assertFalse(ReviewEventType.ERROR.isTerminal());
            assertTrue(ReviewEventType.FAILED.isTerminal());
        }

        @Test
        @DisplayName("新旧类型共存 — 向后兼容")
        void backwardCompatibleTypesExist() {
            assertNotNull(ReviewEventType.TASK_CREATED);
            assertNotNull(ReviewEventType.COMPLETED);
            assertNotNull(ReviewEventType.TASK_STARTED);
            assertNotNull(ReviewEventType.RESULT);
        }
    }
}
