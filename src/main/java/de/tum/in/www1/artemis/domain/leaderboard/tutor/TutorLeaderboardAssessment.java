package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardAssessment {

    private final Long exerciseId;

    private final User user;

    private final long assessments;

    private final long points;

    private final Long courseId;

    public long getAssessments() {
        return assessments;
    }

    public long getPoints() {
        return points;
    }

    public User getUser() {
        return user;
    }

    public Long getCourseId() {
        return courseId;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public TutorLeaderboardAssessment(Long exerciseId, User user, long assessments, long points, Long courseId) {
        this.exerciseId = exerciseId;
        this.user = user;
        this.assessments = assessments;
        this.points = points;
        this.courseId = courseId;
    }
}
