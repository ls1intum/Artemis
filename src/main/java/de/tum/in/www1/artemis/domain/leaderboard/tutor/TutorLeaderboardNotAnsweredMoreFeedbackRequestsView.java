package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "view_tutor_leaderboard_not_answered_more_feedback_requests")
@Immutable
public class TutorLeaderboardNotAnsweredMoreFeedbackRequestsView {

    @EmbeddedId
    private LeaderboardId leaderboardId;

    @Column(name = "all_requests")
    private long allRequests;

    @Column(name = "not_answered_requests")
    private long notAnsweredRequests;

    @Column(name = "points")
    private Long points;

    @Column(name = "course_id")
    private long courseId;

    @Column(name = "first_name")
    private String userFirstName;

    public long getAllRequests() {
        return allRequests;
    }

    public long getNotAnsweredRequests() {
        return notAnsweredRequests;
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
