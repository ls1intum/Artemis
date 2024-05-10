package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import jakarta.annotation.Nullable;

import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Feedback;

public interface AssessmentUpdate {

    List<Feedback> feedbacks();

    ComplaintResponse complaintResponse();

    @Nullable
    String assessmentNote();
}
