package com.prassistant.pr.review.analyzer;

import com.prassistant.pr.diff.model.SanitizedDiff;
import com.prassistant.pr.review.model.Chunk;

/**
 * 提示词组装器 — 将 Chunk / SanitizedDiff 翻译为发给 AI 的 Prompt 字符串
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li>{@link #buildWholeFile(SanitizedDiff)} — 整文件分析（未分块）</li>
 *   <li>{@link #buildChunk(Chunk)} — 单块分析（分块后，含前序摘要）</li>
 * </ul>
 */
public final class ChunkPromptBuilder {

    /** 系统角色设定 */
    static final String SYSTEM_ROLE = """
            你是一位资深的代码评审专家。专注于以下方面：
            - 正确性：逻辑错误、边界条件、异常处理
            - 安全性：注入风险、权限校验、数据加密
            - 性能：N+1查询、资源泄漏、缓存机会
            - 并发：竞态条件、死锁、线程安全
            - 可维护性：代码重复、命名规范、设计模式
            """;

    /** 输出格式要求（通用） */
    static final String OUTPUT_FORMAT = """
            请严格按以下 JSON 格式输出（仅 JSON，不要包含 Markdown 代码块标记或任何额外文字）：
            {
              "summary": "变更摘要（不超过30字）",
              "risks": [
                { "type": "NullPointer|Concurrency|Security|Performance|Maintainability|Other", "line": 行号, "description": "风险说明（不超过100字）" }
              ],
              "suggestions": [
                { "priority": 1-5, "description": "建议内容（不超过100字）" }
              ],
              "riskLevel": "HIGH|MEDIUM|LOW"
            }
            """;

    /** 单块模式额外要求 */
    static final String CHUNK_CROSS_CHUNK_HINT =
            "5. 跨块依赖提示：如果当前块修改了接口签名、删除了方法、新增了全局变量或修改了配置项，"
                    + "请详细描述这些变更，格式：变更内容 -> 影响范围 -> 后续需关注的文件\n";

    /** 反幻觉约束 */
    static final String ANTI_HALLUCINATION =
            "⚠️ 注意事项：\n"
                    + "- 只分析实际存在的代码变更行，不要假设或推断未显示的内容\n"
                    + "- 如果无法确定行号，line 填 0\n";

    private ChunkPromptBuilder() {
        // 工具类，禁止实例化
    }

    /**
     * 构建整文件分析 Prompt
     *
     * <p>适用于文件 Token 数 ≤ 8000 的场景，一次 AI 调用完成整个文件的分析。</p>
     */
    public static String buildWholeFile(SanitizedDiff diff) {
        String filePath = diff.getFilePath() != null ? diff.getFilePath() : "unknown";
        String content = diff.getSanitizedContent() != null ? diff.getSanitizedContent() : "";

        return SYSTEM_ROLE + "\n"
                + "【全局锚点】\n"
                + "文件：" + filePath + "\n"
                + "变更状态：" + diff.getStatus() + "\n"
                + "本文件共 1 个分析单元，当前整文件分析\n\n"
                + "【代码变更】\n"
                + content + "\n\n"
                + "【任务】\n"
                + "请分析这段代码变更，输出：\n"
                + "1. 变更摘要（30字内）\n"
                + "2. 风险点（标注类型：NullPointer/Concurrency/Security/Performance 等）\n"
                + "3. Review 建议\n"
                + "4. 风险评级（HIGH / MEDIUM / LOW）\n\n"
                + ANTI_HALLUCINATION + "\n"
                + OUTPUT_FORMAT;
    }

    /**
     * 构建单块分析 Prompt
     *
     * <p>适用于文件已分块的场景，每块携带全局锚点和前序摘要，独立发给 AI。</p>
     */
    public static String buildChunk(Chunk chunk) {
        String anchor = chunk.getGlobalAnchor() != null ? chunk.getGlobalAnchor() : "未知位置";
        String previous = chunk.getPreviousSummary();
        String content = chunk.getContent() != null ? chunk.getContent() : "";

        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_ROLE).append("\n");
        sb.append("【全局锚点】\n").append(anchor).append("\n\n");

        if (previous != null && !previous.isEmpty()) {
            sb.append("【前序变更摘要】\n").append(previous).append("\n\n");
        }

        sb.append("【代码变更】\n").append(content).append("\n\n");
        sb.append("【任务】\n");
        sb.append("请分析这段代码变更，输出：\n");
        sb.append("1. 变更摘要（30字内）\n");
        sb.append("2. 风险点（标注类型：NullPointer/Concurrency/Security/Performance 等）\n");
        sb.append("3. Review 建议\n");
        sb.append("4. 风险评级（HIGH / MEDIUM / LOW）\n");
        sb.append(CHUNK_CROSS_CHUNK_HINT).append("\n");
        sb.append(ANTI_HALLUCINATION).append("\n");
        sb.append(OUTPUT_FORMAT);

        return sb.toString();
    }
}
