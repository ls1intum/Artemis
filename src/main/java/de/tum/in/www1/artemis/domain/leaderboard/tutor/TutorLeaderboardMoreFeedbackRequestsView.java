package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "view_tutor_leaderboard_more_feedback_requests")
@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorLeaderboardMoreFeedbackRequestsView {

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

    public TutorLeaderboardMoreFeedbackRequestsView() {
    }

    public TutorLeaderboardMoreFeedbackRequestsView(LeaderboardId leaderboardId, long allRequests, long notAnsweredRequests, Long points, long courseId, String userFirstName) {
        this.leaderboardId = leaderboardId;
        this.allRequests = allRequests;
        this.notAnsweredRequests = notAnsweredRequests;
        this.points = points;
        this.courseId = courseId;
        this.userFirstName = userFirstName;
    }
}
