package com.prassistant.pr.orchestrator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReviewTaskTest {

    @Nested
    @DisplayName("Builder 构建")
    class Builder {

        @Test
        @DisplayName("应正确构建 ReviewTask")
        void shouldBuildReviewTask() {
            Instant now = Instant.now();
            ReviewTask task = ReviewTask.builder()
                    .taskId("abc12345")
                    .prUrl("https://github.com/org/repo/pull/1")
                    .state(ReviewState.PENDING)
                    .createdAt(now)
                    .build();

            assertEquals("abc12345", task.getTaskId());
            assertEquals("https://github.com/org/repo/pull/1", task.getPrUrl());
            assertEquals(ReviewState.PENDING, task.getState());
            assertEquals(now, task.getCreatedAt());
        }

        @Test
        @DisplayName("errors 默认应为空列表")
        void shouldDefaultErrorsToEmptyList() {
            ReviewTask task = ReviewTask.builder()
                    .taskId("t1")
                    .build();

            assertNotNull(task.getErrors());
            assertTrue(task.getErrors().isEmpty());
        }
    }

    @Nested
    @DisplayName("状态流转")
    class StateTransition {

        @Test
        @DisplayName("transitionTo 应更新状态和 updatedAt")
        void shouldUpdateStateAndTimestamp() {
            ReviewTask task = ReviewTask.builder()
                    .taskId("t1")
                    .state(ReviewState.PENDING)
                    .createdAt(Instant.now())
                    .build();

            Instant before = task.getUpdatedAt();
            task.transitionTo(ReviewState.L1_SANITIZING);

            assertEquals(ReviewState.L1_SANITIZING, task.getState());
            assertNotNull(task.getUpdatedAt());
            // updatedAt should be newer or equal (may be the same instant)
            assertTrue(task.getUpdatedAt().compareTo(before != null ? before : Instant.MIN) >= 0);
        }

        @Test
        @DisplayName("fail 应将状态设为 FAILED 并记录错误")
        void shouldSetFailedStateAndError() {
            ReviewTask task = ReviewTask.builder()
                    .taskId("t1")
                    .state(ReviewState.L2_ANALYZING)
                    .build();

            task.fail("API timeout");

            assertEquals(ReviewState.FAILED, task.getState());
            assertEquals("API timeout", task.getError());
        }
    }

    @Nested
    @DisplayName("状态判断")
    class StatePredicates {

        @Test
        @DisplayName("PENDING 不是终态也不是运行态")
        void pendingState() {
            assertEquals(ReviewState.PENDING, ReviewState.PENDING);
            assertFalse(ReviewState.PENDING.isTerminal());
            assertFalse(ReviewState.PENDING.isRunning());
        }

        @Test
        @DisplayName("运行中状态应返回 isRunning=true")
        void runningStates() {
            assertTrue(ReviewState.FETCHING_DIFF.isRunning());
            assertTrue(ReviewState.L1_SANITIZING.isRunning());
            assertTrue(ReviewState.L2_ANALYZING.isRunning());
            assertTrue(ReviewState.L3_AGGREGATING.isRunning());
        }

        @Test
        @DisplayName("COMPLETED 和 FAILED 应返回 isTerminal=true")
        void terminalStates() {
            assertTrue(ReviewState.COMPLETED.isTerminal());
            assertTrue(ReviewState.FAILED.isTerminal());
            assertFalse(ReviewState.COMPLETED.isRunning());
            assertFalse(ReviewState.FAILED.isRunning());
        }

        @Test
        @DisplayName("isCompleted 和 isFailed 应正确判断")
        void taskStatePredicates() {
            ReviewTask completed = ReviewTask.builder()
                    .taskId("t1").state(ReviewState.COMPLETED).build();
            ReviewTask failed = ReviewTask.builder()
                    .taskId("t2").state(ReviewState.FAILED).build();
            ReviewTask pending = ReviewTask.builder()
                    .taskId("t3").state(ReviewState.PENDING).build();

            assertTrue(completed.isCompleted());
            assertTrue(completed.isTerminal());
            assertFalse(completed.isFailed());

            assertTrue(failed.isFailed());
            assertTrue(failed.isTerminal());
            assertFalse(failed.isCompleted());

            assertFalse(pending.isTerminal());
        }

        @Test
        @DisplayName("state 为 null 时 isTerminal 应返回 false")
        void shouldHandleNullState() {
            ReviewTask task = ReviewTask.builder().taskId("t1").build();
            assertFalse(task.isTerminal());
            assertFalse(task.isCompleted());
            assertFalse(task.isFailed());
        }
    }

    @Nested
    @DisplayName("错误收集")
    class ErrorCollection {

        @Test
        @DisplayName("addError 应追加到列表")
        void shouldAddError() {
            ReviewTask task = ReviewTask.builder()
                    .taskId("t1")
                    .state(ReviewState.L2_ANALYZING)
                    .build();

            task.addError("File UserService.java 分析超时");

            assertEquals(1, task.getErrors().size());
            assertTrue(task.getErrors().get(0).contains("UserService.java"));
        }

        @Test
        @DisplayName("多次 addError 应追加多条")
        void shouldAddMultipleErrors() {
            ReviewTask task = ReviewTask.builder()
                    .taskId("t1")
                    .state(ReviewState.L2_ANALYZING)
                    .errors(new ArrayList<>(List.of("err1")))
                    .build();

            task.addError("err2");
            task.addError("err3");

            assertEquals(3, task.getErrors().size());
        }

        @Test
        @DisplayName("errors 为 null 时 addError 应初始化列表")
        void shouldInitializeListWhenNull() {
            ReviewTask task = ReviewTask.builder()
                    .taskId("t1")
                    .errors(null)
                    .build();

            task.addError("test error");

            assertEquals(1, task.getErrors().size());
        }
    }
}
