package de.tum.cit.aet.artemis.atlas.domain.practice;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * A PracticeSession represents an on-demand adaptive practice quiz session requested by a student.
 * It tracks when the practice started, when it finished, and the overall score, isolated from formal grades.
 */
@Entity
@Table(name = "practice_session")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PracticeSession extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Parent
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @Parent
    private Course course;

    @Column(name = "start_time", nullable = false)
    private ZonedDateTime startTime;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "score")
    private Double score;

    @Column(name = "max_points")
    private Double maxPoints;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(ZonedDateTime startTime) {
        this.startTime = startTime;
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(ZonedDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(Double maxPoints) {
        this.maxPoints = maxPoints;
    }

    @Override
    public String toString() {
        return "PracticeSession{" + "id=" + getId() + ", user=" + (user != null ? user.getId() : null) + ", course=" + (course != null ? course.getId() : null) + ", startTime="
                + startTime + ", completedAt=" + completedAt + ", score=" + score + ", maxPoints=" + maxPoints + '}';
    }
}
