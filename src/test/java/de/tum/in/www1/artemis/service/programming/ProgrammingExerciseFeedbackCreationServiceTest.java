package de.tum.in.www1.artemis.service.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

class ProgrammingExerciseFeedbackCreationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "progexfeedbackcreaiontest";

    @Autowired
    private ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    @Test
    void staticCodeAnalysisReportNotTruncatedFurther() {
        final String longText = "0".repeat(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH * 2);

        final var scaIssue = new StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue();
        scaIssue.setCategory("scaCategory");
        scaIssue.setMessage(longText);
        scaIssue.setStartColumn(0);
        scaIssue.setEndColumn(123);
        scaIssue.setFilePath("some/long/file/Path.java");

        final StaticCodeAnalysisReportDTO scaReport = new StaticCodeAnalysisReportDTO();
        scaReport.setTool(StaticCodeAnalysisTool.CHECKSTYLE);
        scaReport.setIssues(List.of(scaIssue));

        final List<Feedback> scaFeedbacks = feedbackCreationService.createFeedbackFromStaticCodeAnalysisReports(List.of(scaReport));
        assertThat(scaFeedbacks).hasSize(1);

        final Feedback scaFeedback = scaFeedbacks.get(0);
        assertThat(scaFeedback.getHasLongFeedbackText()).isFalse();
        assertThat(scaFeedback.getLongFeedback()).isEmpty();
        assertThat(scaFeedback.getDetailText()).hasSizeGreaterThan(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH)
                .hasSizeLessThanOrEqualTo(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH);
    }
}
