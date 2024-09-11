package de.tum.cit.aet.artemis.web.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Feedback;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingAssessmentDTO(List<Feedback> feedbacks, String assessmentNote) {
}
