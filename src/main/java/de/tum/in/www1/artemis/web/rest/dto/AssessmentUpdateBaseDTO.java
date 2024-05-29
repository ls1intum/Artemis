package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Feedback;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface AssessmentUpdateBaseDTO {

    List<Feedback> feedbacks();

    ComplaintResponse complaintResponse();

    @Nullable
    String assessmentNote();
}
