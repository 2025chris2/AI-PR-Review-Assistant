package com.prassistant.pr.orchestrator.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

class ReviewEventPublisherTest {

    private ReviewEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ReviewEventPublisher(360_000L);
    }

    private ReviewEvent sampleEvent(ReviewEventType type, String taskId) {
        return ReviewEvent.stageEvent(type, taskId, "test message");
    }

    @Nested
    @DisplayName("register() — 注册 SSE 连接")
    class Register {

        @Test
        @DisplayName("应返回有效 SseEmitter 并增加计数")
        void shouldRegisterEmitter() {
            SseEmitter emitter = publisher.register("task-1");

            assertNotNull(emitter);
            assertEquals(1, publisher.countEmitters("task-1"));
        }

        @Test
        @DisplayName("同一任务多次注册应累加计数")
        void shouldSupportMultipleEmitters() {
            publisher.register("task-1");
            publisher.register("task-1");
            publisher.register("task-1");

            assertEquals(3, publisher.countEmitters("task-1"));
        }

        @Test
        @DisplayName("不同任务的 emitters 应独立计数")
        void shouldSeparateEmittersByTask() {
            publisher.register("task-1");
            publisher.register("task-2");

            assertEquals(1, publisher.countEmitters("task-1"));
            assertEquals(1, publisher.countEmitters("task-2"));
        }
    }

    @Nested
    @DisplayName("publish() — 事件推送")
    class Publish {

        @Test
        @DisplayName("推送事件到已注册的连接不应抛异常")
        void shouldPublishEventWithoutError() {
            publisher.register("task-1");

            assertDoesNotThrow(() ->
                    publisher.publish(sampleEvent(ReviewEventType.L1_COMPLETE, "task-1")));
        }

        @Test
        @DisplayName("无注册连接时应静默跳过")
        void shouldSkipWhenNoSubscribers() {
            assertDoesNotThrow(() ->
                    publisher.publish(sampleEvent(ReviewEventType.L1_COMPLETE, "no-such-task")));
        }

        @Test
        @DisplayName("emitter 完成后推送应自动清理")
        void shouldHandleCompletedEmitterGracefully() throws Exception {
            SseEmitter emitter = publisher.register("task-1");
            emitter.complete(); // 主动完成

            // 推送应自动检测并清理已完成的 emitter
            assertDoesNotThrow(() ->
                    publisher.publish(sampleEvent(ReviewEventType.L2_COMPLETE, "task-1")));
        }

        @Test
        @DisplayName("推送时使用 sseName 而非枚举名（不抛异常即为通过）")
        void shouldUseSseNameForEventName() {
            publisher.register("sse-task");

            ReviewEvent event = ReviewEvent.stageEvent(
                    ReviewEventType.L2_FILE_START, "sse-task", "start");

            assertDoesNotThrow(() -> publisher.publish(event));
            assertEquals("l2.file.start", event.getType().getSseName());
        }
    }

    @Nested
    @DisplayName("complete() — 完成连接")
    class Complete {

        @Test
        @DisplayName("完成后应清除注册表")
        void shouldClearEmittersAfterComplete() {
            publisher.register("task-1");
            publisher.register("task-1");

            publisher.complete("task-1");

            assertEquals(0, publisher.countEmitters("task-1"));
        }

        @Test
        @DisplayName("不存在的任务应静默处理")
        void shouldHandleNonexistentTask() {
            assertDoesNotThrow(() -> publisher.complete("no-such-task"));
        }
    }

    @Nested
    @DisplayName("SSE 超时常量")
    class Timeout {

        @Test
        @DisplayName("默认 SSE 超时应为 360 秒（6 分钟）")
        void defaultTimeoutShouldBe360Seconds() {
            assertEquals(360_000L, ReviewEventPublisher.DEFAULT_SSE_TIMEOUT);
        }
    }
}
