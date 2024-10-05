package de.tum.cit.aet.artemis.assessment.dto.tutor;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query, we cannot use primitive types here, because otherwise Hibernate gets confused
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardAnsweredMoreFeedbackRequestsDTO(Long userId, Long answeredRequests, Double points) {

    public TutorLeaderboardAnsweredMoreFeedbackRequestsDTO() {
        this(0L, 0L, 0.0);
    }

    @Override
    @NotNull
    public Long userId() {
        return userId != null ? userId : 0L;
    }

    @Override
    @NotNull
    public Long answeredRequests() {
        return answeredRequests != null ? answeredRequests : 0L;
    }

    @Override
    @NotNull
    public Double points() {
        return points != null ? points : 0.0;
    }
}
