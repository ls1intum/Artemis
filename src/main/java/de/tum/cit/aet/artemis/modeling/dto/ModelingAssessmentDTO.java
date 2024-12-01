package de.tum.cit.aet.artemis.modeling.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingAssessmentDTO(List<Feedback> feedbacks, String assessmentNote) {
}
