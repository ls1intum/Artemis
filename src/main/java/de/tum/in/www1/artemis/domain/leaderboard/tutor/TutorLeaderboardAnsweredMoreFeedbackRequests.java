package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardAnsweredMoreFeedbackRequests {

    private final long exerciseId;

    private final long userId;

    private final long answeredRequests;

    private final double points;

    private final long courseId;

    public long getAnsweredRequests() {
        return answeredRequests;
    }

    public double getPoints() {
        return points;
    }

    public long getUserId() {
        return userId;
    }

    public long getCourseId() {
        return courseId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public TutorLeaderboardAnsweredMoreFeedbackRequests(long exerciseId, long userId, long answeredRequests, double points, long courseId) {
        this.exerciseId = exerciseId;
        this.userId = userId;
        this.answeredRequests = answeredRequests;
        this.points = points;
        this.courseId = courseId;
    }
}
