package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardAssessments {

    private final long userId;

    private final long assessments;

    private final double points;

    private final double averageScore;

    private final double averageRating;

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

    public double getAverageRating() {
        return averageRating;
    }

    public TutorLeaderboardAssessments(Long userId, Long assessments, Double points, Double averageScore, Double averageRating) {
        this.userId = userId;
        this.assessments = assessments;
        this.points = points;
        this.averageScore = averageScore == null ? 0 : averageScore;
        this.averageRating = averageRating == null ? 0 : averageRating;
    }

    public TutorLeaderboardAssessments() {
        this.userId = 0L;
        this.assessments = 0L;
        this.points = 0.0;
        this.averageScore = 0.0;
        this.averageRating = 0.0;
    }

    public Long getKey() {
        return userId;
    }
}
