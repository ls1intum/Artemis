package de.tum.in.www1.artemis.web.rest.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Feedback;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadAssessmentDTO(List<Feedback> feedbacks, String assessmentNote) {

    /**
     * Creates a new FileUploadAssessmentDTO and makes sure that feedbacks is never null.
     */
    public FileUploadAssessmentDTO {
        if (feedbacks == null) {
            feedbacks = new ArrayList<>();
        }
    }
}
