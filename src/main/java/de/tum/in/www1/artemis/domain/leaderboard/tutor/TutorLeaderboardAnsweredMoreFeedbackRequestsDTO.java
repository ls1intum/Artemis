package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardAnsweredMoreFeedbackRequestsDTO(long userId, long answeredRequests, double points) {

    public TutorLeaderboardAnsweredMoreFeedbackRequestsDTO(Long userId, Long answeredRequests, Double points) {
        this(userId == null ? 0 : userId, answeredRequests == null ? 0 : answeredRequests, points == null ? 0 : points);
    }

    public TutorLeaderboardAnsweredMoreFeedbackRequestsDTO() {
        this(0L, 0L, 0.0);
    }

}
