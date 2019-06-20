package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "view_tutor_leaderboard_complaints")
@Immutable
public class TutorLeaderboardComplaintsView {

    @Id
    @Column(name = "uuid")
    @JsonIgnore
    private String uuid;

    @Column(name = "complaints")
    private Long complaints;

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

    public Long getComplaints() {
        return complaints;
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
