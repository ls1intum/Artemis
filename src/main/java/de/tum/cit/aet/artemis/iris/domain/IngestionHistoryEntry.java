package de.tum.cit.aet.artemis.iris.domain;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageToolActivityConverter;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;

/**
 * A persisted record of one finished or failed lecture ingestion, backing the admin dashboard's recent-history view so
 * it survives an Artemis restart. Old entries are pruned by a scheduled retention job. The per-step timeline is stored
 * as JSON via {@link IrisMessageToolActivityConverter}.
 */
@Entity
@Table(name = "iris_ingestion_history")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IngestionHistoryEntry extends DomainObject {

    @Column(name = "job_id", length = 100)
    private String jobId;

    @Column(name = "course_id")
    private long courseId;

    @Column(name = "lecture_id")
    private long lectureId;

    @Column(name = "lecture_unit_id")
    private long lectureUnitId;

    @Column(name = "lecture_unit_name")
    @Nullable
    private String lectureUnitName;

    @Column(name = "lecture_name")
    @Nullable
    private String lectureName;

    @Column(name = "outcome", length = 20, nullable = false)
    private String outcome;

    @Column(name = "started_at")
    @Nullable
    private ZonedDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private ZonedDateTime finishedAt;

    @Column(name = "total_millis")
    @Nullable
    private Long totalMillis;

    @Column(name = "failed_step_name")
    @Nullable
    private String failedStepName;

    @Column(name = "error_message", length = 2000)
    @Nullable
    private String errorMessage;

    @Column(name = "activities", columnDefinition = "longtext")
    @Convert(converter = IrisMessageToolActivityConverter.class)
    @Nullable
    private List<PyrisActivityDTO> activities;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public long getLectureId() {
        return lectureId;
    }

    public void setLectureId(long lectureId) {
        this.lectureId = lectureId;
    }

    public long getLectureUnitId() {
        return lectureUnitId;
    }

    public void setLectureUnitId(long lectureUnitId) {
        this.lectureUnitId = lectureUnitId;
    }

    @Nullable
    public String getLectureUnitName() {
        return lectureUnitName;
    }

    public void setLectureUnitName(@Nullable String lectureUnitName) {
        this.lectureUnitName = lectureUnitName;
    }

    @Nullable
    public String getLectureName() {
        return lectureName;
    }

    public void setLectureName(@Nullable String lectureName) {
        this.lectureName = lectureName;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    @Nullable
    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(@Nullable ZonedDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public ZonedDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(ZonedDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    @Nullable
    public Long getTotalMillis() {
        return totalMillis;
    }

    public void setTotalMillis(@Nullable Long totalMillis) {
        this.totalMillis = totalMillis;
    }

    @Nullable
    public String getFailedStepName() {
        return failedStepName;
    }

    public void setFailedStepName(@Nullable String failedStepName) {
        this.failedStepName = failedStepName;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(@Nullable String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Nullable
    public List<PyrisActivityDTO> getActivities() {
        return activities;
    }

    public void setActivities(@Nullable List<PyrisActivityDTO> activities) {
        this.activities = activities;
    }
}
