package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.*;

import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "view_tutor_leaderboard_accepted_complaints")
@Immutable
public class TutorLeaderboardComplaintsView {

    @EmbeddedId
    private LeaderboardId leaderboardId;

    @Column(name = "all_complaints")
    private long allComplaints;

    @Column(name = "accepted_complaints")
    private long acceptedComplaints;

    @Column(name = "points")
    private Long points;

    @Column(name = "course_id")
    private long courseId;

    @Column(name = "first_name")
    private String userFirstName;

    public long getAllComplaints() {
        return allComplaints;
    }

    public long getAcceptedComplaints() {
        return acceptedComplaints;
    }

    public Long getPoints() {
        return points;
    }

    public long getUserId() {
        return leaderboardId.getUserId();
    }

    public long getCourseId() {
        return courseId;
    }

    public long getExerciseId() {
        return leaderboardId.getExerciseId();
    }

    public String getUserFirstName() {
        return userFirstName;
    }
}
