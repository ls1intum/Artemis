package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardComplaintResponses {

    private final long exerciseId;

    private final long userId;

    private final long complaintResponses;

    private final long points;

    private final long courseId;

    public long getComplaintResponses() {
        return complaintResponses;
    }

    public long getPoints() {
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

    public TutorLeaderboardComplaintResponses(long exerciseId, long userId, long complaintResponses, long points, long courseId) {
        this.exerciseId = exerciseId;
        this.userId = userId;
        this.complaintResponses = complaintResponses;
        this.points = points;
        this.courseId = courseId;
    }
}
