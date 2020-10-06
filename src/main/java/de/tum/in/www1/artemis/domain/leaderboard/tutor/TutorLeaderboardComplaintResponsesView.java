package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "view_tutor_leaderboard_complaint_responses")
@Immutable
public class TutorLeaderboardComplaintResponsesView {

    @EmbeddedId
    private LeaderboardId leaderboardId;

    @Column(name = "complaint_responses")
    private long complaintResponses;

    @Column(name = "points")
    private Long points;

    @Column(name = "course_id")
    private long courseId;

    @Column(name = "first_name")
    private String userFirstName;

    public long getComplaintResponses() {
        return complaintResponses;
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

    public TutorLeaderboardComplaintResponsesView() {
    }

    public TutorLeaderboardComplaintResponsesView(LeaderboardId leaderboardId, long complaintResponses, Long points, long courseId, String userFirstName) {
        this.leaderboardId = leaderboardId;
        this.complaintResponses = complaintResponses;
        this.points = points;
        this.courseId = courseId;
        this.userFirstName = userFirstName;
    }
}
