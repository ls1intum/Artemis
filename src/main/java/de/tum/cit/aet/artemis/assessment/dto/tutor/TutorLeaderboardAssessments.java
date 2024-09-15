package de.tum.cit.aet.artemis.assessment.dto.tutor;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardAssessments(Long userId, Long assessments, Double points, Double averageScore, Double averageRating, Long numberOfRatings) {

    public TutorLeaderboardAssessments() {
        this(0L, 0L, 0.0, 0.0, 0.0, 0L);
    }

    @Override
    @NotNull
    public Long userId() {
        return userId != null ? userId : 0L;
    }

    @Override
    @NotNull
    public Long assessments() {
        return assessments != null ? assessments : 0L;
    }

    @Override
    @NotNull
    public Double points() {
        return points != null ? points : 0.0;
    }

    @Override
    @NotNull
    public Double averageScore() {
        return averageScore != null ? averageScore : 0.0;
    }

    @Override
    @NotNull
    public Double averageRating() {
        return averageRating != null ? averageRating : 0.0;
    }

    @Override
    @NotNull
    public Long numberOfRatings() {
        return numberOfRatings != null ? numberOfRatings : 0L;
    }
}
