package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "view_tutor_leaderboard_complaint_responses")
@Immutable
public class TutorLeaderboardComplaintResponsesView {

    @Id
    @Column(name = "uuid")
    @JsonIgnore
    private String uuid;

    @Column(name = "complaint_responses")
    private Long complaintResponses;

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

    public Long getComplaintResponses() {
        return complaintResponses;
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
