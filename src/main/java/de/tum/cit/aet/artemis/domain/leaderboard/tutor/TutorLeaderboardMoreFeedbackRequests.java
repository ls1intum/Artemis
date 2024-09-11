package de.tum.cit.aet.artemis.domain.leaderboard.tutor;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardMoreFeedbackRequests(Long userId, Long allRequests, Long notAnsweredRequests, Double points) {

    public TutorLeaderboardMoreFeedbackRequests() {
        this(0L, 0L, 0L, 0.0);
    }

    @Override
    @NotNull
    public Long userId() {
        return userId != null ? userId : 0L;
    }

    @Override
    @NotNull
    public Long allRequests() {
        return allRequests != null ? allRequests : 0L;
    }

    @Override
    @NotNull
    public Long notAnsweredRequests() {
        return notAnsweredRequests != null ? notAnsweredRequests : 0L;
    }

    @Override
    @NotNull
    public Double points() {
        return points != null ? points : 0.0;
    }
}
