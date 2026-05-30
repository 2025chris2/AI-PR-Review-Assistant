package com.prassistant.pr.review.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * Token 预估工具类 — 封装 jtokkit 提供统一的 Token 计算
 *
 * <p>使用 cl100k_base 编码（GPT-4 / DeepSeek 兼容），提供三种方法：</p>
 * <ul>
 *   <li>{@link #count(String)} — 纯文本 Token 数</li>
 *   <li>{@link #count(String, EncodingType)} — 指定编码的 Token 数</li>
 *   <li>{@link #exceedsThreshold(String, int)} — 是否超过阈值</li>
 * </ul>
 */
public final class TokenEstimator {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding DEFAULT_ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    /** 默认分块触发阈值（单位：Token） */
    public static final int DEFAULT_CHUNK_THRESHOLD = 8000;

    private TokenEstimator() {
        // 工具类，禁止实例化
    }

    /**
     * 计算指定文本的 Token 数（使用 cl100k_base 编码）
     */
    public static int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return DEFAULT_ENCODING.countTokens(text);
    }

    /**
     * 使用指定编码类型计算文本 Token 数
     */
    public static int count(String text, EncodingType encodingType) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Encoding encoding = REGISTRY.getEncoding(encodingType);
        return encoding.countTokens(text);
    }

    /**
     * 判断文本 Token 数是否超过指定阈值
     */
    public static boolean exceedsThreshold(String text, int threshold) {
        return count(text) > threshold;
    }

    /**
     * 判断文本 Token 数是否超过默认阈值（8000）
     */
    public static boolean exceedsThreshold(String text) {
        return exceedsThreshold(text, DEFAULT_CHUNK_THRESHOLD);
    }
}
