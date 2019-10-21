package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.*;

import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "view_tutor_leaderboard_assessments")
@Immutable
public class TutorLeaderboardAssessmentView {

    @EmbeddedId
    private LeaderboardId leaderboardId;

    @Column(name = "assessments")
    private Long assessments;

    @Column(name = "points")
    private Long points;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "first_name")
    private String userFirstName;

    public Long getAssessments() {
        return assessments;
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
