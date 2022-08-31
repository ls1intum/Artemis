package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Note: this POJO is used in a JPA query in ComplaintRepository and therefore cannot easily be converted into a record
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
        this(0L, 0L, 0L, 0.0);
    }
}
