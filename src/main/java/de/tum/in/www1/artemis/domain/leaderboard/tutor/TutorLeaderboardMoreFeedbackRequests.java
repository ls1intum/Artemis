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

    public TutorLeaderboardMoreFeedbackRequests(long userId, long allRequests, long notAnsweredRequests, double points) {
        this.userId = userId;
        this.allRequests = allRequests;
        this.notAnsweredRequests = notAnsweredRequests;
        this.points = points;
    }
}
