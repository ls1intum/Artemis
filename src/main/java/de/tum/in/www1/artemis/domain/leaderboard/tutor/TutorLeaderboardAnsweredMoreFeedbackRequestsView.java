package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "view_tutor_leaderboard_answered_more_feedback_requests")
@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardAnsweredMoreFeedbackRequestsView {

    @EmbeddedId
    private LeaderboardId leaderboardId;

    @Column(name = "answered_requests")
    private long answeredRequests;

    @Column(name = "points")
    private Long points;

    @Column(name = "course_id")
    private long courseId;

    @Column(name = "first_name")
    private String userFirstName;

    public long getAnsweredRequests() {
        return answeredRequests;
    }

    public long getPoints() {
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

    public TutorLeaderboardAnsweredMoreFeedbackRequestsView() {
    }

    public TutorLeaderboardAnsweredMoreFeedbackRequestsView(LeaderboardId leaderboardId, long answeredRequests, Long points, long courseId, String userFirstName) {
        this.leaderboardId = leaderboardId;
        this.answeredRequests = answeredRequests;
        this.points = points;
        this.courseId = courseId;
        this.userFirstName = userFirstName;
    }
}
