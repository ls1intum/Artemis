package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.User;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardAssessment {

    private final long exerciseId;

    private final User user;

    private final long assessments;

    private final double points;

    private final long courseId;

    public long getAssessments() {
        return assessments;
    }

    public double getPoints() {
        return points;
    }

    public User getUser() {
        return user;
    }

    public long getCourseId() {
        return courseId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public TutorLeaderboardAssessment(long exerciseId, User user, long assessments, double points, long courseId) {
        this.exerciseId = exerciseId;
        this.user = user;
        this.assessments = assessments;
        this.points = points;
        this.courseId = courseId;
    }
}
