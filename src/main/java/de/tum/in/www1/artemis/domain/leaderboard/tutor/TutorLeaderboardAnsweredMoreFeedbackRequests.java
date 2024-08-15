package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardAnsweredMoreFeedbackRequests(Long userId, Long answeredRequests, Double points) {

    public TutorLeaderboardAnsweredMoreFeedbackRequests() {
        this(0L, 0L, 0.0);
    }

    @Override
    @NonNull
    public Long userId() {
        return userId != null ? userId : 0L;
    }

    @Override
    @NonNull
    public Long answeredRequests() {
        return answeredRequests != null ? answeredRequests : 0L;
    }

    @Override
    @NonNull
    public Double points() {
        return points != null ? points : 0.0;
    }
}
