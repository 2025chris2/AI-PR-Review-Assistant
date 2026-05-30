package com.prassistant.pr.review.model;

import com.prassistant.pr.diff.model.FileChangeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 第二层最终产出 — 文件级 Review 报告
 *
 * <p>无论该文件是整文件分析还是分块聚合的，第三层看到的都是同一个
 * {@code FileReviewReport}。通过 {@link #analysisMethod} 标识处理方式。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileReviewReport {

    /** 文件路径 */
    private String filePath;

    /** 变更状态（added / modified / removed / renamed） */
    private FileChangeType status;

    /** 文件级变更总结（50 字内） */
    private String overallSummary;

    /** 文件级风险评级 */
    private RiskLevel riskLevel;

    /** 合并去重后的风险列表 */
    private List<ChunkReviewResult.RiskItem> risks;

    /** 合并排序后的建议列表 */
    private List<ChunkReviewResult.SuggestionItem> suggestions;

    /** 跨块/跨 Hunk 发现的一致性问题 */
    private List<String> crossChunkIssues;

    /** 分析方法（整文件 or 分块） */
    private AnalysisMethod analysisMethod;

    /** 错误信息（分析失败时填充，正常为 null） */
    private String error;

    /** 分析方式枚举 */
    public enum AnalysisMethod {
        WHOLE_FILE,
        CHUNKED
    }

    /** 风险评级枚举 */
    public enum RiskLevel {
        HIGH, MEDIUM, LOW
    }

    /** 是否分析成功 */
    public boolean isSuccess() {
        return error == null;
    }
}
