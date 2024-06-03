package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardMoreFeedbackRequestsDTO(long userId, long allRequests, long notAnsweredRequests, double points) {

    public TutorLeaderboardMoreFeedbackRequestsDTO(Long userId, Long allRequests, Long notAnsweredRequests, Double points) {
        this(userId == null ? 0 : userId, allRequests == null ? 0 : allRequests, notAnsweredRequests == null ? 0 : notAnsweredRequests, points == null ? 0 : points);
    }

    public TutorLeaderboardMoreFeedbackRequestsDTO() {
        this(0L, 0L, 0L, 0.0);
    }
}
