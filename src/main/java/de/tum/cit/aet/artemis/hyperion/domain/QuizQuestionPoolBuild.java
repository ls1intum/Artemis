package de.tum.cit.aet.artemis.hyperion.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * One build of a course's quiz question pool: what started it, when it ran, and how many items it enqueued, generated, judged and
 * failed. The counts describe this build's own work, not the pool as a whole.
 */
@Entity
@Table(name = "quiz_question_pool_build")
public class QuizQuestionPoolBuild extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @Parent
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private QuizQuestionPoolBuildTrigger trigger;

    @Column(name = "started_at", nullable = false)
    private ZonedDateTime startedAt = ZonedDateTime.now();

    @Column(name = "finished_at")
    @Nullable
    private ZonedDateTime finishedAt;

    @Column(name = "enqueued_count", nullable = false)
    private int enqueued;

    @Column(name = "generated_count", nullable = false)
    private int generated;

    @Column(name = "judged_count", nullable = false)
    private int judged;

    @Column(name = "accepted_count", nullable = false)
    private int accepted;

    @Column(name = "rejected_count", nullable = false)
    private int rejected;

    @Column(name = "failed_count", nullable = false)
    private int failed;

    /** Why the build stopped before working the pool to completion; {@code null} when it ran through. */
    @Column(name = "failure", length = 500)
    @Nullable
    private String failure;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public QuizQuestionPoolBuildTrigger getTrigger() {
        return trigger;
    }

    public void setTrigger(QuizQuestionPoolBuildTrigger trigger) {
        this.trigger = trigger;
    }

    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(ZonedDateTime startedAt) {
        this.startedAt = startedAt;
    }

    @Nullable
    public ZonedDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(@Nullable ZonedDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getEnqueued() {
        return enqueued;
    }

    public void setEnqueued(int enqueued) {
        this.enqueued = enqueued;
    }

    public int getGenerated() {
        return generated;
    }

    public void setGenerated(int generated) {
        this.generated = generated;
    }

    public int getJudged() {
        return judged;
    }

    public void setJudged(int judged) {
        this.judged = judged;
    }

    public int getAccepted() {
        return accepted;
    }

    public void setAccepted(int accepted) {
        this.accepted = accepted;
    }

    public int getRejected() {
        return rejected;
    }

    public void setRejected(int rejected) {
        this.rejected = rejected;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    @Nullable
    public String getFailure() {
        return failure;
    }

    public void setFailure(@Nullable String failure) {
        this.failure = failure;
    }
}
