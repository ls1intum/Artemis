package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardAssessments {

    private final long userId;

    private final long assessments;

    private final double points;

    private final double averageScore;

    public long getUserId() {
        return userId;
    }

    public long getAssessments() {
        return assessments;
    }

    public double getPoints() {
        return points;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public TutorLeaderboardAssessments(Long userId, Long assessments, Double points, Double averageScore) {
        this.userId = userId;
        this.assessments = assessments;
        this.points = points;
        this.averageScore = averageScore;
    }

    public TutorLeaderboardAssessments() {
        this.userId = 0L;
        this.assessments = 0L;
        this.points = 0.0;
        this.averageScore = 0.0;
    }

    public Long getKey() {
        return userId;
    }
}
