package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "build_job")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildJob extends DomainObject {

    @Column(name = "exercise_id")
    private Long exerciseId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "participation_id")
    private Long participationId;

    @Column(name = "build_start_date")
    private ZonedDateTime buildStartDate;

    @Column(name = "build_completion_date")
    private ZonedDateTime buildCompletionDate;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "exception_occurred")
    private boolean exceptionOccurred;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public void setParticipationId(Long participationId) {
        this.participationId = participationId;
    }

    public ZonedDateTime getBuildStartDate() {
        return buildStartDate;
    }

    public void setBuildStartDate(ZonedDateTime buildStartDate) {
        this.buildStartDate = buildStartDate;
    }

    public ZonedDateTime getBuildCompletionDate() {
        return buildCompletionDate;
    }

    public void setBuildCompletionDate(ZonedDateTime buildCompletionDate) {
        this.buildCompletionDate = buildCompletionDate;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public boolean isExceptionOccurred() {
        return exceptionOccurred;
    }

    public void setExceptionOccurred(boolean exceptionOccurred) {
        this.exceptionOccurred = exceptionOccurred;
    }
}
