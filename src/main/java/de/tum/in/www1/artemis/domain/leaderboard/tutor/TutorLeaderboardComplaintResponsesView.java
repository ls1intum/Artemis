package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.*;

import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "view_tutor_leaderboard_complaint_responses")
@Immutable
public class TutorLeaderboardComplaintResponsesView {

    @EmbeddedId
    private LeaderboardId leaderboardId;

    @Column(name = "complaint_responses")
    private Long complaintResponses;

    @Column(name = "points")
    private Long points;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "first_name")
    private String userFirstName;

    public Long getComplaintResponses() {
        return complaintResponses;
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
