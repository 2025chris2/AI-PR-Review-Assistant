package com.prassistant.pr.aggregation;

import com.prassistant.pr.aggregation.model.GlobalReviewReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassifyIssueTypeTest {

    @Test
    @DisplayName("浮点关键词应匹配 NUMERICAL_ACCURACY")
    void shouldClassifyFloatingPointAsNumericalAccuracy() {
        assertEquals(GlobalReviewReport.IssueType.NUMERICAL_ACCURACY,
            GlobalAggregator.classifyIssueType("实现文件中的浮点数比较（r[j][j]==0）可能因精度导致误判"));
    }

    @Test
    @DisplayName("容差关键词应匹配 NUMERICAL_ACCURACY")
    void shouldClassifyToleranceAsNumericalAccuracy() {
        assertEquals(GlobalReviewReport.IssueType.NUMERICAL_ACCURACY,
            GlobalAggregator.classifyIssueType("浮点比较未使用容差"));
    }

    @Test
    @DisplayName("精度关键词应匹配 NUMERICAL_ACCURACY")
    void shouldClassifyPrecisionAsNumericalAccuracy() {
        assertEquals(GlobalReviewReport.IssueType.NUMERICAL_ACCURACY,
            GlobalAggregator.classifyIssueType("存在浮点精度问题"));
    }

    @Test
    @DisplayName("无匹配关键词应返回 OTHER")
    void shouldReturnOtherForUnmatched() {
        assertEquals(GlobalReviewReport.IssueType.OTHER,
            GlobalAggregator.classifyIssueType("一般性代码风格问题"));
    }

    @Test
    @DisplayName("性能关键词应匹配 PERFORMANCE")
    void shouldClassifyPerformance() {
        assertEquals(GlobalReviewReport.IssueType.PERFORMANCE,
            GlobalAggregator.classifyIssueType("存在性能问题和内存分配开销"));
    }

    @Test
    @DisplayName("算法选型关键词应匹配 ALGORITHM_CHOICE")
    void shouldClassifyAlgorithmChoice() {
        assertEquals(GlobalReviewReport.IssueType.ALGORITHM_CHOICE,
            GlobalAggregator.classifyIssueType("Gram Schmidt算法选型存在问题，应使用Householder"));
    }
}
