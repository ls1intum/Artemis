package de.tum.in.www1.artemis.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

class FeedbackRepositoryTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Test
    void copyFeedbackWithLongFeedback() {
        final String longText = "0".repeat(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 10);

        final Feedback feedback = new Feedback();
        feedback.setHasLongFeedbackText(true);
        feedback.setDetailText(longText);
        feedback.setCredits(1.0);

        final Feedback savedFeedback = feedbackRepository.save(feedback);

        final Feedback copiedFeedback = savedFeedback.copyFeedback();
        assertThat(copiedFeedback.getLongFeedbackText()).isNotNull();
        assertThat(copiedFeedback.getLongFeedbackText().getText()).isEqualTo(longText);

        final Feedback newSavedFeedback = feedbackRepository.save(copiedFeedback);
        assertThat(newSavedFeedback.getId()).isNotEqualTo(savedFeedback.getId());
        assertThat(newSavedFeedback.getLongFeedbackText().getId()).isEqualTo(newSavedFeedback.getId());
    }

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

        final List<Feedback> scaFeedbacks = feedbackRepository.createFeedbackFromStaticCodeAnalysisReports(List.of(scaReport));
        assertThat(scaFeedbacks).hasSize(1);

        final Feedback scaFeedback = scaFeedbacks.get(0);
        assertThat(scaFeedback.getHasLongFeedbackText()).isFalse();
        assertThat(scaFeedback.getLongFeedbackText()).isNull();
        assertThat(scaFeedback.getDetailText()).hasSizeGreaterThan(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH)
                .hasSizeLessThanOrEqualTo(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH);
    }
}
