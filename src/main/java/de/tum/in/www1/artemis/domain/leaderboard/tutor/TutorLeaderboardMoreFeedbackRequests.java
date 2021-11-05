package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardMoreFeedbackRequests {

    private final long userId;

    private final long allRequests;

    private final long notAnsweredRequests;

    private final double points;

    public long getAllRequests() {
        return allRequests;
    }

    public long getNotAnsweredRequests() {
        return notAnsweredRequests;
    }

    public double getPoints() {
        return points;
    }

    public long getUserId() {
        return userId;
    }

    public TutorLeaderboardMoreFeedbackRequests(Long userId, Long allRequests, Long notAnsweredRequests, Double points) {
        this.userId = userId == null ? 0 : userId;
        this.allRequests = allRequests == null ? 0 : allRequests;
        this.notAnsweredRequests = notAnsweredRequests == null ? 0 : notAnsweredRequests;
        this.points = points == null ? 0 : points;
    }

    public TutorLeaderboardMoreFeedbackRequests() {
        this.userId = 0L;
        this.allRequests = 0L;
        this.notAnsweredRequests = 0L;
        this.points = 0.0;
    }

    public Long getKey() {
        return userId;
    }
}
