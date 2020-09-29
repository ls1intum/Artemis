package de.tum.in.www1.artemis.service.connectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.domain.enumeration.TextAssessmentConflictType;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.service.dto.TextAssessmentConflictRequestDTO;
import de.tum.in.www1.artemis.service.dto.TextAssessmentConflictResponseDTO;

public class TextAssessmentConflictServiceTest {

    private static final String TEXT_ASSESSMENT_CONFLICT_ENDPOINT = "http://localhost:8001/feedback_consistency";

    /**
     * Creates two submissions with feedback and send them to remote Athene service to check feedback consistency.
     * Checks if the consistency is found correctly.
     * @throws NetworkingError if the request isn't successful
     */
    @Test
    public void checkFeedbackConsistency() throws NetworkingError {
        final TextAssessmentConflictService textAssessmentConflictService = new TextAssessmentConflictService();
        ReflectionTestUtils.setField(textAssessmentConflictService, "API_ENDPOINT", TEXT_ASSESSMENT_CONFLICT_ENDPOINT);

        final List<TextAssessmentConflictRequestDTO> textAssessmentConflictRequestDTOS = new ArrayList<>();

        String firstSubmissionText = "My answer text block for the question.";
        String firstFeedbackText = "Correct answer.";
        final TextAssessmentConflictRequestDTO firstRequestObject = new TextAssessmentConflictRequestDTO("1", firstSubmissionText, 1L, 1L, firstFeedbackText, 1.0);
        textAssessmentConflictRequestDTOS.add(firstRequestObject);

        textAssessmentConflictService.checkFeedbackConsistencies(textAssessmentConflictRequestDTOS, -1L, 0);
        textAssessmentConflictRequestDTOS.clear();

        String secondSubmissionText = "My answer text block for the question.";
        String secondFeedbackText = "Correct answer.";
        final TextAssessmentConflictRequestDTO secondRequestObject = new TextAssessmentConflictRequestDTO("2", secondSubmissionText, 1L, 2L, secondFeedbackText, 2.0);
        textAssessmentConflictRequestDTOS.add(secondRequestObject);

        List<TextAssessmentConflictResponseDTO> feedbackConflicts = textAssessmentConflictService.checkFeedbackConsistencies(textAssessmentConflictRequestDTOS, -1L, 0);
        assertThat(feedbackConflicts, is(not(empty())));
        assertThat(feedbackConflicts, hasItem(
                either(hasProperty("firstFeedbackId", is(firstRequestObject.getFeedbackId()))).or(hasProperty("secondFeedbackId", is(firstRequestObject.getFeedbackId())))));
        assertThat(feedbackConflicts, hasItem(
                either(hasProperty("firstFeedbackId", is(secondRequestObject.getFeedbackId()))).or(hasProperty("secondFeedbackId", is(secondRequestObject.getFeedbackId())))));
        assertThat(feedbackConflicts, hasItem(hasProperty("type", is(TextAssessmentConflictType.INCONSISTENT_SCORE))));
    }

    @BeforeAll
    public static void runClassOnlyIfTextAssessmentConflictServiceIsAvailable() {
        assumeTrue(isTextAssessmentConflictServiceAvailable());
    }

    private static boolean isTextAssessmentConflictServiceAvailable() {
        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(TEXT_ASSESSMENT_CONFLICT_ENDPOINT).openConnection();
            httpURLConnection.setRequestMethod("HEAD");
            httpURLConnection.setConnectTimeout(1000);
            final int responseCode = httpURLConnection.getResponseCode();

            return responseCode == 405;
        }
        catch (IOException e) {
            return false;
        }
    }
}
