package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.*;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "view_tutor_leaderboard_assessments")
@Immutable
public class TutorLeaderboardAssessmentView {

    @Id
    @Column(name = "uuid")
    @JsonIgnore
    private String uuid;

    @Column(name = "assessments")
    private Long assessments;

    @Column(name = "points")
    private Long points;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "first_name")
    private String userFirstName;

    @Column(name = "title")
    private String courseTitle;

    public Long getAssessments() {
        return assessments;
    }

    public Long getPoints() {
        return points;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getUserFirstName() {
        return userFirstName;
    }

    public String getCourseTitle() {
        return courseTitle;
    }
}
