package de.tum.cit.aet.artemis.assessment.dto;

import java.util.List;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface AssessmentUpdateBaseDTO {

    List<Feedback> feedbacks();

    ComplaintResponse complaintResponse();

    @Nullable
    String assessmentNote();
}
