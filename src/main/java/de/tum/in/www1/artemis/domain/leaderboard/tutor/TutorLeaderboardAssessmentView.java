package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "view_tutor_leaderboard_assessments")
@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardAssessmentView {

    @EmbeddedId
    private LeaderboardId leaderboardId;

    @Column(name = "assessments")
    private long assessments;

    @Column(name = "points")
    private Long points;

    @Column(name = "course_id")
    private long courseId;

    @Column(name = "first_name")
    private String userFirstName;

    public long getAssessments() {
        return assessments;
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

    public TutorLeaderboardAssessmentView() {
    }

    public TutorLeaderboardAssessmentView(LeaderboardId leaderboardId, long assessments, Long points, long courseId, String userFirstName) {
        this.leaderboardId = leaderboardId;
        this.assessments = assessments;
        this.points = points;
        this.courseId = courseId;
        this.userFirstName = userFirstName;
    }
}
