package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackMessage;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.repository.ScaFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;

@ExtendWith(MockitoExtension.class)
class ProgrammingFeedbackSynthesizerServiceTest {

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Mock
    private TestCaseFeedbackRepository testCaseFeedbackRepository;

    @Mock
    private ScaFeedbackRepository scaFeedbackRepository;

    @Mock
    private TestCasePointsService testCasePointsService;

    @InjectMocks
    private ProgrammingFeedbackSynthesizerService synthesizerService;

    @Test
    void shouldKeepSynthesizedScaDetailTextValidWhenJsonEscapingExceedsTheColumnLimit() throws Exception {
        Result result = new Result();
        result.setId(42L);

        ScaFeedback scaFeedback = new ScaFeedback();
        scaFeedback.setId(7L);
        scaFeedback.setTool(StaticCodeAnalysisTool.CHECKSTYLE);
        scaFeedback.setCategory("Code Style");
        scaFeedback.setRule("AvoidEscapedStrings");
        scaFeedback.setMessage(message("\"😀".repeat(1_500)));
        result.addScaFeedback(scaFeedback);

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        when(testCaseFeedbackRepository.findWithTestCaseAndMessageByResultId(result.getId())).thenReturn(List.of());

        synthesizerService.attachSynthesizedFeedback(result, exercise, false);

        Feedback view = result.getFeedbacks().stream().findFirst().orElseThrow();
        assertThat(view.getDetailText()).hasSizeLessThanOrEqualTo(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH);
        StaticCodeAnalysisIssue issue = objectMapper.readValue(view.getDetailText(), StaticCodeAnalysisIssue.class);
        assertThat(issue.message()).isNotEmpty().isSubstringOf(scaFeedback.getMessageText());
    }

    private static FeedbackMessage message(String text) {
        FeedbackMessage message = new FeedbackMessage();
        message.setText(text);
        return message;
    }
}
