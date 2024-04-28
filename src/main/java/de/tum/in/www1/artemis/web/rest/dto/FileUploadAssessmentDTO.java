package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import de.tum.in.www1.artemis.domain.Feedback;

public record FileUploadAssessmentDTO(List<Feedback> feedbacks, String assessmentNote) {
}
