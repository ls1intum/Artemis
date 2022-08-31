package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Note: this POJO is used in a JPA query in ComplaintRepository and therefore cannot easily be converted into a record
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

    public TutorLeaderboardComplaintResponses(Long userId, Long complaintResponses, Double points) {
        this.userId = userId == null ? 0 : userId;
        this.complaintResponses = complaintResponses == null ? 0 : complaintResponses;
        this.points = points == null ? 0 : points;
    }

    public TutorLeaderboardComplaintResponses() {
        this(0L, 0L, 0.0);
    }
}
