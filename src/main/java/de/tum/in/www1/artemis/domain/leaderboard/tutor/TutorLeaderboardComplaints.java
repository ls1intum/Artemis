package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardComplaints {

    private final long exerciseId;

    private final long userId;

    private final long allComplaints;

    private final long acceptedComplaints;

    private final long points;

    private final long courseId;

    public long getAllComplaints() {
        return allComplaints;
    }

    public long getAcceptedComplaints() {
        return acceptedComplaints;
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

    public TutorLeaderboardComplaints(long exerciseId, long userId, long allComplaints, long acceptedComplaints, long points, long courseId) {
        this.exerciseId = exerciseId;
        this.userId = userId;
        this.allComplaints = allComplaints;
        this.acceptedComplaints = acceptedComplaints;
        this.points = points;
        this.courseId = courseId;
    }
}
