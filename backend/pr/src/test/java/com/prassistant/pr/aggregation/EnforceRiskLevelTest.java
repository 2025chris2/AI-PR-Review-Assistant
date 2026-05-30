package com.prassistant.pr.aggregation;

import com.prassistant.pr.aggregation.model.GlobalReviewReport;
import com.prassistant.pr.review.model.ChunkReviewResult;
import com.prassistant.pr.review.model.FileReviewReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnforceRiskLevelTest {

    @Test
    @DisplayName("NUMERICAL_ACCURACY with AI severity=HIGH should NOT force global HIGH")
    void numericalAccuracyHighSeverityShouldNotForceGlobalHigh() {
        var issue = GlobalReviewReport.CrossFileIssue.builder()
                .issueType(GlobalReviewReport.IssueType.NUMERICAL_ACCURACY)
                .description("Gram-Schmidt数值不稳定")
                .severity("HIGH")
                .build();

        GlobalReviewReport.RiskLevel result = RiskPrioritizer.enforceRiskLevel(
                GlobalReviewReport.RiskLevel.LOW,
                null,
                List.of(issue));

        assertEquals(GlobalReviewReport.RiskLevel.MEDIUM,
                result, "数值精度问题应最高为 MEDIUM，不应因 AI severity=HIGH 拉到 HIGH");
    }

    @Test
    @DisplayName("SECURITY_VULNERABILITY with AI severity=HIGH SHOULD force global HIGH")
    void securityVulnerabilityHighSeverityShouldForceGlobalHigh() {
        var issue = GlobalReviewReport.CrossFileIssue.builder()
                .issueType(GlobalReviewReport.IssueType.SECURITY_VULNERABILITY)
                .description("SQL注入风险")
                .severity("HIGH")
                .build();

        GlobalReviewReport.RiskLevel result = RiskPrioritizer.enforceRiskLevel(
                GlobalReviewReport.RiskLevel.LOW,
                null,
                List.of(issue));

        assertEquals(GlobalReviewReport.RiskLevel.HIGH,
                result, "安全漏洞 severity=HIGH 必须强制全局 HIGH");
    }

    @Test
    @DisplayName("Fatal risk keywords in description SHOULD force global HIGH")
    void fatalKeywordsInDescriptionShouldForceHigh() {
        var issue = GlobalReviewReport.CrossFileIssue.builder()
                .issueType(GlobalReviewReport.IssueType.OTHER)
                .description("可能导致空指针崩溃")
                .severity("MEDIUM")
                .build();

        GlobalReviewReport.RiskLevel result = RiskPrioritizer.enforceRiskLevel(
                GlobalReviewReport.RiskLevel.LOW,
                null,
                List.of(issue));

        assertEquals(GlobalReviewReport.RiskLevel.HIGH,
                result, "包含'空指针'致命关键词必须强制 HIGH");
    }

    @Test
    @DisplayName("No issues should preserve original risk level")
    void noIssuesShouldPreserveOriginalLevel() {
        GlobalReviewReport.RiskLevel result = RiskPrioritizer.enforceRiskLevel(
                GlobalReviewReport.RiskLevel.LOW,
                null,
                null);

        assertEquals(GlobalReviewReport.RiskLevel.LOW, result);
    }
}
