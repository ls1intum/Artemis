package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardAnsweredMoreFeedbackRequests {

    private final long userId;

    private final long answeredRequests;

    private final double points;

    public long getAnsweredRequests() {
        return answeredRequests;
    }

    public double getPoints() {
        return points;
    }

    public long getUserId() {
        return userId;
    }

    public TutorLeaderboardAnsweredMoreFeedbackRequests(Long userId, Long answeredRequests, Double points) {
        this.userId = userId;
        this.answeredRequests = answeredRequests;
        this.points = points;
    }

    public TutorLeaderboardAnsweredMoreFeedbackRequests() {
        this.userId = 0L;
        this.answeredRequests = 0L;
        this.points = 0.0;
    }

    public Long getKey() {
        return userId;
    }
}
