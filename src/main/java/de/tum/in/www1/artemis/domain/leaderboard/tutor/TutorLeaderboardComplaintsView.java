package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "view_tutor_leaderboard_complaints")
@Immutable
public class TutorLeaderboardComplaintsView {

    @EmbeddedId
    private LeaderboardId leaderboardId;

    @Column(name = "all_complaints")
    private long allComplaints;

    @Column(name = "accepted_complaints")
    private long acceptedComplaints;

    @Column(name = "points")
    private Long points;

    @Column(name = "course_id")
    private long courseId;

    @Column(name = "first_name")
    private String userFirstName;

    public long getAllComplaints() {
        return allComplaints;
    }

    public long getAcceptedComplaints() {
        return acceptedComplaints;
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

    public TutorLeaderboardComplaintsView() {
    }

    public TutorLeaderboardComplaintsView(LeaderboardId leaderboardId, long allComplaints, long acceptedComplaints, Long points, long courseId, String userFirstName) {
        this.leaderboardId = leaderboardId;
        this.allComplaints = allComplaints;
        this.acceptedComplaints = acceptedComplaints;
        this.points = points;
        this.courseId = courseId;
        this.userFirstName = userFirstName;
    }
}
