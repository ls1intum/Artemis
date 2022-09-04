package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Note: this POJO is used in a JPA query in ComplaintRepository and therefore cannot easily be converted into a record
public class TutorLeaderboardComplaints {

    private final long userId;

    private final long allComplaints;

    private final long acceptedComplaints;

    private final double points;

    public long getAllComplaints() {
        return allComplaints;
    }

    public long getAcceptedComplaints() {
        return acceptedComplaints;
    }

    public double getPoints() {
        return points;
    }

    public long getUserId() {
        return userId;
    }

    public TutorLeaderboardComplaints(Long userId, Long allComplaints, Long acceptedComplaints, Double points) {
        this.userId = userId;
        this.allComplaints = allComplaints;
        this.acceptedComplaints = acceptedComplaints;
        this.points = points;
    }

    public TutorLeaderboardComplaints() {
        this(0L, 0L, 0L, 0.0);
    }
}
