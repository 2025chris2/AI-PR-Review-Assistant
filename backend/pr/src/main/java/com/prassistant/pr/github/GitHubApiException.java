package com.prassistant.pr.github;

/**
 * GitHub API 调用异常
 *
 * <p>包装 HTTP 错误响应（401、403、404、429 等）以及网络/解析错误。
 * 携带 statusCode 以便上层逻辑（如 Controller）将其映射为对应的 HTTP 响应状态。</p>
 */
public class GitHubApiException extends RuntimeException {

    private final int statusCode;

    // ========== 构造方法 ==========

    public GitHubApiException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public GitHubApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    // ========== Getter ==========

    public int getStatusCode() {
        return statusCode;
    }

    // ========== 静态工厂方法 ==========

    /** 404 — PR 或仓库不存在 */
    public static GitHubApiException notFound(String owner, String repo, int prNumber) {
        return new GitHubApiException(404,
                "PR not found: " + owner + "/" + repo + "#" + prNumber);
    }

    /** 401 — Token 无效或过期 */
    public static GitHubApiException badCredentials() {
        return new GitHubApiException(401, "GitHub token is invalid or expired");
    }

    /** 403 / 429 — API 频率限制 */
    public static GitHubApiException rateLimited(String resetTime) {
        return new GitHubApiException(403,
                "GitHub API rate limit exceeded. Resets at: " + resetTime);
    }
}
