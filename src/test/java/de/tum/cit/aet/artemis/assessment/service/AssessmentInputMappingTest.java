package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintResponseInputDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradingInstructionDTO;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class AssessmentInputMappingTest {

    @Test
    void feedbackKeepsItsIdentityAndUsesResolvedLongTextWithAnIdOnlyInstructionReference() {
        var instruction = new GradingInstruction();
        instruction.setId(12L);
        instruction.setCredits(3.0);
        var dto = new FeedbackDTO(34L, "Feedback", "preview", true, "reference", 2.0, true, FeedbackType.MANUAL, Visibility.AFTER_DUE_DATE, GradingInstructionDTO.of(instruction),
                null);
        var feedback = dto.toAssessmentFeedback("Full long feedback text");
        assertThat(feedback.getId()).isEqualTo(34L);
        assertThat(feedback.getDetailText()).isEqualTo("Full long feedback text");
        assertThat(feedback.getCredits()).isEqualTo(2.0);
        assertThat(feedback.getVisibility()).isEqualTo(Visibility.AFTER_DUE_DATE);
        assertThat(feedback.getGradingInstruction()).isNotSameAs(instruction);
        assertThat(feedback.getGradingInstruction().getId()).isEqualTo(12L);
        assertThat(feedback.getGradingInstruction().getCredits()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void complaintMappingRetainsDecisionAndLockIdInBothAssessmentRequestTypes(boolean modeling) {
        var type = modeling ? de.tum.cit.aet.artemis.modeling.dto.ComplaintResponseRequestDTO.class : de.tum.cit.aet.artemis.text.dto.ComplaintResponseRequestDTO.class;
        ComplaintResponseInputDTO dto = JsonObjectMapper.get().readValue("{\"id\":12,\"responseText\":\"Accepted\",\"complaint\":{\"id\":34,\"accepted\":true}}", type);
        var response = dto.toEntity();
        assertThat(response.getId()).isEqualTo(12L);
        assertThat(response.getResponseText()).isEqualTo("Accepted");
        assertThat(response.getComplaint().getId()).isEqualTo(34L);
        assertThat(response.getComplaint().isAccepted()).isTrue();
        var absentComplaint = JsonObjectMapper.get().readValue("{}", type).toEntity();
        assertThat(absentComplaint.getComplaint()).isNotNull();
        assertThat(absentComplaint.getComplaint().getId()).isNull();
    }
}
