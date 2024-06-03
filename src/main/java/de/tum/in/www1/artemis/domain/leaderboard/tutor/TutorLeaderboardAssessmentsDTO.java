package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorLeaderboardAssessmentsDTO(long userId, long assessments, double points, double averageScore, double averageRating, long numberOfRatings) {

    public TutorLeaderboardAssessmentsDTO(Long userId, Long assessments, Double points, Double averageScore, Double averageRating, Long numberOfRatings) {
        this(userId == null ? 0 : userId, assessments == null ? 0 : assessments, points == null ? 0 : points, averageScore == null ? 0 : averageScore,
                averageRating == null ? 0 : averageRating, numberOfRatings == null ? 0 : numberOfRatings);
    }

    public TutorLeaderboardAssessmentsDTO() {
        this(0L, 0L, 0.0, 0.0, 0.0, 0L);
    }

}
