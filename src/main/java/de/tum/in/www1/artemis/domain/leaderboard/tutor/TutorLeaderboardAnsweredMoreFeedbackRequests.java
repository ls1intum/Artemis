package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Note: this POJO is used in a JPA query in ComplaintRepository and therefore cannot easily be converted into a record
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
        this.userId = userId == null ? 0 : userId;
        this.answeredRequests = answeredRequests == null ? 0 : answeredRequests;
        this.points = points == null ? 0 : points;
    }

    public TutorLeaderboardAnsweredMoreFeedbackRequests() {
        this(0L, 0L, 0.0);
    }
}
