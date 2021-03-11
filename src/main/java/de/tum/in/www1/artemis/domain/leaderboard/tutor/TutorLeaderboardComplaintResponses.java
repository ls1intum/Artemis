package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardComplaintResponses {

    private final long userId;

    private final long complaintResponses;

    private final double points;

    public long getComplaintResponses() {
        return complaintResponses;
    }

    public double getPoints() {
        return points;
    }

    public long getUserId() {
        return userId;
    }

    public TutorLeaderboardComplaintResponses(long userId, long complaintResponses, double points) {
        this.userId = userId;
        this.complaintResponses = complaintResponses;
        this.points = points;
    }
}
