package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardAssessments(Long userId, Long assessments, Double points, Double averageScore, Double averageRating, Long numberOfRatings) {

    public TutorLeaderboardAssessments() {
        this(0L, 0L, 0.0, 0.0, 0.0, 0L);
    }

    @Override
    @NonNull
    public Long userId() {
        return userId != null ? userId : 0L;
    }

    @Override
    @NonNull
    public Long assessments() {
        return assessments != null ? assessments : 0L;
    }

    @Override
    @NonNull
    public Double points() {
        return points != null ? points : 0.0;
    }

    @Override
    @NonNull
    public Double averageScore() {
        return averageScore != null ? averageScore : 0.0;
    }

    @Override
    @NonNull
    public Double averageRating() {
        return averageRating != null ? averageRating : 0.0;
    }

    @Override
    @NonNull
    public Long numberOfRatings() {
        return numberOfRatings != null ? numberOfRatings : 0L;
    }
}
