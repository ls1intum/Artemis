package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.*;

import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "view_tutor_leaderboard_complaints")
@Immutable
public class TutorLeaderboardComplaintsView {

    @EmbeddedId
    private LeaderboardId leaderboardId;

    @Column(name = "complaints")
    private Long complaints;

    @Column(name = "points")
    private Long points;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "first_name")
    private String userFirstName;

    public Long getComplaints() {
        return complaints;
    }

    public Long getPoints() {
        return points;
    }

    public Long getUserId() {
        return leaderboardId.getUserId();
    }

    public Long getCourseId() {
        return courseId;
    }

    public Long getExerciseId() {
        return leaderboardId.getExerciseId();
    }

    public String getUserFirstName() {
        return userFirstName;
    }
}
