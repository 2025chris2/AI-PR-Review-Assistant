package com.prassistant.pr.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prassistant.pr.aggregation.model.GlobalReviewReport;
import com.prassistant.pr.aggregation.model.PrMetadata;
import com.prassistant.pr.github.GitHubApiClient;
import com.prassistant.pr.github.GitHubApiException;
import com.prassistant.pr.github.GitHubPrData;
import com.prassistant.pr.orchestrator.ReviewOrchestrator;
import com.prassistant.pr.orchestrator.event.ReviewEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReviewOrchestrator orchestrator;

    @Mock
    private ReviewEventPublisher eventPublisher;

    @Mock
    private GitHubApiClient gitHubApiClient;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // 同步执行器，让 async 任务在当前线程立即执行
        ReviewController controller = new ReviewController(
                orchestrator, eventPublisher, gitHubApiClient, Runnable::run);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ReviewRequest sampleRequest() {
        return new ReviewRequest(
                "https://github.com/org/repo/pull/1",
                "diff --git a/Test.java b/Test.java\n@@ -1,1 +1,1 @@\n-old\n+new",
                "feat: add login",
                "Add login endpoint",
                "chris",
                "main",
                "feat/login",
                2,
                80,
                10,
                java.util.List.of("Java: 2")
        );
    }

    @Nested
    @DisplayName("POST /api/v1/reviews — 创建 Review")
    class CreateReview {

        @Test
        @DisplayName("有效请求应返回 202 和 taskId")
        void shouldReturn202WithTaskId() throws Exception {
            when(orchestrator.review(anyString(), any(), anyString()))
                    .thenReturn(GlobalReviewReport.builder()
                            .overallSummary("test")
                            .globalRiskLevel(GlobalReviewReport.RiskLevel.LOW)
                            .build());

            mockMvc.perform(post("/api/v1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.taskId").isNotEmpty())
                    .andExpect(jsonPath("$.eventsUrl").isString())
                    .andExpect(jsonPath("$.resultUrl").isString());

            // TASK_STARTED (sync) + RESULT (async) = 2 publishes
            verify(eventPublisher, times(2)).publish(any());
            verify(orchestrator).review(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("空 diff 应返回 400")
        void shouldReturn400ForEmptyDiff() throws Exception {
            ReviewRequest bad = new ReviewRequest(
                    "url", "", "title", "desc", "author",
                    "main", "feat", 0, 0, 0, null);

            mockMvc.perform(post("/api/v1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("null diff 应返回 400")
        void shouldReturn400ForNullDiff() throws Exception {
            ReviewRequest bad = new ReviewRequest(
                    "url", null, "title", "desc", "author",
                    "main", "feat", 0, 0, 0, null);

            mockMvc.perform(post("/api/v1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reviews/{taskId}/events — SSE 事件流")
    class StreamEvents {

        @Test
        @DisplayName("应返回 SseEmitter")
        void shouldReturnSseEmitter() throws Exception {
            when(eventPublisher.register("task-1")).thenReturn(new SseEmitter());

            mockMvc.perform(get("/api/v1/reviews/task-1/events"))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted());

            verify(eventPublisher).register("task-1");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reviews/{taskId}/result — 查询结果")
    class GetResult {

        @Test
        @DisplayName("未完成应返回 202")
        void shouldReturn202WhenPending() throws Exception {
            mockMvc.perform(get("/api/v1/reviews/nonexistent/result"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/reviews/github — 从 GitHub 创建 Review")
    class CreateReviewFromGithub {

        @Test
        @DisplayName("有效请求应返回 202 和 taskId")
        void shouldReturn202WithTaskId() throws Exception {
            PrMetadata metadata = PrMetadata.builder()
                    .prUrl("https://github.com/org/repo/pull/1")
                    .title("test")
                    .author("octocat")
                    .build();
            GitHubPrData prData = new GitHubPrData(metadata, "diff --git a/Test.java b/Test.java\n@@ -1,1 +1,1 @@\n-old\n+new");

            when(gitHubApiClient.fetchAll(eq("octocat"), eq("hello-world"), eq(1), eq("token-abc")))
                    .thenReturn(prData);
            when(orchestrator.review(anyString(), any()))
                    .thenReturn(GlobalReviewReport.builder()
                            .overallSummary("test")
                            .globalRiskLevel(GlobalReviewReport.RiskLevel.LOW)
                            .build());

            GitHubReviewRequest request = new GitHubReviewRequest("octocat", "hello-world", 1, "token-abc");

            mockMvc.perform(post("/api/v1/reviews/github")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.taskId").isNotEmpty())
                    .andExpect(jsonPath("$.eventsUrl").isString())
                    .andExpect(jsonPath("$.resultUrl").isString());

            verify(gitHubApiClient).fetchAll("octocat", "hello-world", 1, "token-abc");
            verify(orchestrator).review(anyString(), any());
        }

        @Test
        @DisplayName("空 owner 应返回 400")
        void shouldReturn400ForEmptyOwner() throws Exception {
            GitHubReviewRequest request = new GitHubReviewRequest("", "hello-world", 1, "token");

            mockMvc.perform(post("/api/v1/reviews/github")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("owner is required"));
        }

        @Test
        @DisplayName("空 repo 应返回 400")
        void shouldReturn400ForEmptyRepo() throws Exception {
            GitHubReviewRequest request = new GitHubReviewRequest("octocat", "", 1, "token");

            mockMvc.perform(post("/api/v1/reviews/github")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("repo is required"));
        }

        @Test
        @DisplayName("无效 prNumber 应返回 400")
        void shouldReturn400ForInvalidPrNumber() throws Exception {
            GitHubReviewRequest request = new GitHubReviewRequest("octocat", "hello-world", 0, "token");

            mockMvc.perform(post("/api/v1/reviews/github")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("prNumber must be positive"));
        }

        @Test
        @DisplayName("空 token 应返回 400")
        void shouldReturn400ForEmptyToken() throws Exception {
            GitHubReviewRequest request = new GitHubReviewRequest("octocat", "hello-world", 1, "");

            mockMvc.perform(post("/api/v1/reviews/github")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("token is required"));
        }

        @Test
        @DisplayName("GitHub 404 应返回 404")
        void shouldReturn404OnGitHubNotFound() throws Exception {
            when(gitHubApiClient.fetchAll(anyString(), anyString(), anyInt(), anyString()))
                    .thenThrow(GitHubApiException.notFound("octocat", "hello-world", 999));

            GitHubReviewRequest request = new GitHubReviewRequest("octocat", "hello-world", 999, "token");

            mockMvc.perform(post("/api/v1/reviews/github")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("not found")));
        }

        @Test
        @DisplayName("GitHub 401 应返回 401")
        void shouldReturn401OnBadCredentials() throws Exception {
            when(gitHubApiClient.fetchAll(anyString(), anyString(), anyInt(), anyString()))
                    .thenThrow(GitHubApiException.badCredentials());

            GitHubReviewRequest request = new GitHubReviewRequest("octocat", "hello-world", 1, "bad-token");

            mockMvc.perform(post("/api/v1/reviews/github")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("token")));
        }

        @Test
        @DisplayName("GitHub 403 应返回 403")
        void shouldReturn403OnRateLimit() throws Exception {
            when(gitHubApiClient.fetchAll(anyString(), anyString(), anyInt(), anyString()))
                    .thenThrow(GitHubApiException.rateLimited("1234567890"));

            GitHubReviewRequest request = new GitHubReviewRequest("octocat", "hello-world", 1, "token");

            mockMvc.perform(post("/api/v1/reviews/github")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("rate limit")));
        }
    }
}
