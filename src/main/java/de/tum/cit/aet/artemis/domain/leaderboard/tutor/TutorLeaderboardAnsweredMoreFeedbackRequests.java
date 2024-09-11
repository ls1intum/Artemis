package de.tum.cit.aet.artemis.domain.leaderboard.tutor;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardAnsweredMoreFeedbackRequests(Long userId, Long answeredRequests, Double points) {

    public TutorLeaderboardAnsweredMoreFeedbackRequests() {
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
