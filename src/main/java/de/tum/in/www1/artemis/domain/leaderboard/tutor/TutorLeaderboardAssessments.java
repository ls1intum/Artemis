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

    private final long numberOfRatings;

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

    public long getNumberOfRatings() {
        return numberOfRatings;
    }

    public TutorLeaderboardAssessments(Long userId, Long assessments, Double points, Double averageScore, Double averageRating, Long numberOfRatings) {
        this.userId = userId == null ? 0 : userId;
        this.assessments = assessments == null ? 0 : assessments;
        this.points = points == null ? 0 : points;
        this.averageScore = averageScore == null ? 0 : averageScore;
        this.averageRating = averageRating == null ? 0 : averageRating;
        this.numberOfRatings = numberOfRatings == null ? 0 : numberOfRatings;
    }

    public TutorLeaderboardAssessments() {
        this.userId = 0L;
        this.assessments = 0L;
        this.points = 0.0;
        this.averageScore = 0.0;
        this.averageRating = 0.0;
        this.numberOfRatings = 0L;
    }

    public Long getKey() {
        return userId;
    }
}
