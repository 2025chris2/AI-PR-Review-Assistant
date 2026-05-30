package com.prassistant.pr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PR Review 系统配置属性
 *
 * <p>前缀 {@code pr.review}，可从 application.yml 或环境变量覆盖。</p>
 */
@ConfigurationProperties(prefix = "pr.review")
public class PrReviewProperties {

    /** L2 整批分析超时（秒） */
    private long l2TimeoutSeconds = 60;

    /** L3 全局聚合超时（秒） */
    private long l3TimeoutSeconds = 30;

    /** SSE 连接超时（毫秒） */
    private long sseTimeoutMs = 180_000L;

    /** 最大并发文件分析数 */
    private int maxConcurrentFiles = 5;

    public long getL2TimeoutSeconds() {
        return l2TimeoutSeconds;
    }

    public void setL2TimeoutSeconds(long l2TimeoutSeconds) {
        this.l2TimeoutSeconds = l2TimeoutSeconds;
    }

    public long getL3TimeoutSeconds() {
        return l3TimeoutSeconds;
    }

    public void setL3TimeoutSeconds(long l3TimeoutSeconds) {
        this.l3TimeoutSeconds = l3TimeoutSeconds;
    }

    public long getSseTimeoutMs() {
        return sseTimeoutMs;
    }

    public void setSseTimeoutMs(long sseTimeoutMs) {
        this.sseTimeoutMs = sseTimeoutMs;
    }

    public int getMaxConcurrentFiles() {
        return maxConcurrentFiles;
    }

    public void setMaxConcurrentFiles(int maxConcurrentFiles) {
        this.maxConcurrentFiles = maxConcurrentFiles;
    }
}
