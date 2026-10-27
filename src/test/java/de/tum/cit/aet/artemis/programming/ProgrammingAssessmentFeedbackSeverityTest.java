package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackSeverity;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingAssessmentResultDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingAssessmentUpdateDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingManualResultRequestDTO;

class ProgrammingAssessmentFeedbackSeverityTest {

    private final JsonMapper mapper = JsonObjectMapper.get();

    @ParameterizedTest
    @EnumSource(FeedbackSeverity.class)
    @NullSource
    void severitySurvivesSavingAndResubmittingAnAssessment(FeedbackSeverity severity) {
        var request = mapper.readValue(requestBody(severity), ProgrammingManualResultRequestDTO.class);
        Result result = request.toEntity();

        assertThat(result.getFeedbacks()).extracting(Feedback::getSeverity).containsExactly(severity);
        assertSeveritySurvivesResponseAndNextSave(result, severity);
    }

    @ParameterizedTest
    @EnumSource(FeedbackSeverity.class)
    @NullSource
    void severitySurvivesAComplaintUpdateAndSubsequentSave(FeedbackSeverity severity) {
        var request = mapper.readValue(requestBody(severity), ProgrammingAssessmentUpdateDTO.class);
        var update = request.toAssessmentUpdate();

        assertThat(update.feedbacks()).extracting(Feedback::getSeverity).containsExactly(severity);
        assertSeveritySurvivesResponseAndNextSave(new Result().feedbacks(update.feedbacks()), severity);
    }

    @Test
    void requestsWithoutSeverityRemainSupported() {
        String json = """
                {"feedbacks":[{"type":"MANUAL","credits":1.0}]}
                """;

        var result = mapper.readValue(json, ProgrammingManualResultRequestDTO.class).toEntity();
        var update = mapper.readValue(json, ProgrammingAssessmentUpdateDTO.class).toAssessmentUpdate();

        assertThat(result.getFeedbacks()).allSatisfy(feedback -> assertThat(feedback.getSeverity()).isNull());
        assertThat(update.feedbacks()).allSatisfy(feedback -> assertThat(feedback.getSeverity()).isNull());
    }

    private String requestBody(FeedbackSeverity severity) {
        return """
                {"feedbacks":[{"type":"MANUAL","credits":1.0,"severity":%s}]}
                """.formatted(mapper.writeValueAsString(severity));
    }

    private void assertSeveritySurvivesResponseAndNextSave(Result result, FeedbackSeverity severity) {
        String response = mapper.writeValueAsString(ProgrammingAssessmentResultDTO.of(result));
        var nextSave = mapper.readValue(response, ProgrammingManualResultRequestDTO.class).toEntity();

        assertThat(nextSave.getFeedbacks()).extracting(Feedback::getSeverity).containsExactly(severity);
    }
}
