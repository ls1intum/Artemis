package de.tum.cit.aet.artemis.course.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Holds course-level configuration values that are only needed in specific flows and should not widen the (already large)
 * {@code course} table nor be loaded on every course fetch. The association from {@link Course} is lazy, so this entity is
 * only materialized when explicitly accessed (e.g. the course settings form or the data-privacy cleanup logic).
 * <p>
 * Currently stores the grade-relevance flag that drives the GDPR retention period for a course's student data
 * (grade-relevant courses are retained longer than non-grade-relevant ones), the data-retention hold that suspends that
 * cleanup entirely, the timestamps recording the retention lifecycle, and the per-course Atlas auto-orchestration
 * settings.
 * <p>
 * The auto-orchestration settings live here rather than in an Atlas-owned table because they must be readable and
 * writable even when the Atlas module is disabled: the course update flow has to preserve them, and it cannot go
 * through an {@code @Conditional(AtlasEnabled)} bean to do so.
 */
@Entity
@Table(name = "course_configuration")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseConfiguration extends DomainObject {

    public static final String ENTITY_NAME = "courseConfiguration";

    @OneToOne(mappedBy = "courseConfiguration", fetch = FetchType.LAZY)
    @JsonIgnore
    private Course course;

    /**
     * Whether the course is grade-relevant (e.g. its results count towards official grades / exam records). Grade-relevant
     * courses have a longer legal data-retention period than non-grade-relevant ones. Defaults to {@code true} so that
     * courses are treated as grade-relevant unless an instructor explicitly opts out.
     */
    @Column(name = "grade_relevant", nullable = false)
    private boolean gradeRelevant = true;

    /**
     * Whether the course is under a data-retention hold. A pending objection or legal proceeding legally extends the
     * retention period until it concludes, so a held course is excluded from both phases of the data-privacy cleanup: it
     * is neither warned nor reset for as long as the hold lasts. Defaults to {@code false}.
     */
    @Column(name = "data_retention_hold", nullable = false)
    private boolean dataRetentionHold = false;

    /**
     * When the data-privacy cleanup sent the instructors the "student data will be deleted after the grace period"
     * warning (and archived the course). It stays {@code null} until the course has actually been warned. The reset phase
     * only deletes student data of courses whose {@code resetWarningSentDate + grace} has elapsed, so this anchors the
     * grace period to the real warning event (not to the course end date) and guarantees a course is never reset before
     * its instructors were warned.
     */
    @Column(name = "reset_warning_sent_date")
    private ZonedDateTime resetWarningSentDate;

    /**
     * When the data-privacy cleanup actually reset (deleted) the course's student data. Stays {@code null} until the
     * reset has run. It makes the retention lifecycle one-shot (not warned → warned → reset), so an already-reset course
     * is excluded from both the warn and reset phases and enabled cron jobs never re-warn or re-reset an emptied course.
     */
    @Column(name = "student_data_reset_date")
    private ZonedDateTime studentDataResetDate;

    /**
     * Hard per-course kill switch for the Atlas auto-orchestration pipeline. Even with the global {@code Feature.AtlasAgent}
     * toggle on, a course only participates in the debounce / scheduler pipeline when this flag is set.
     */
    @Column(name = "auto_orchestrator_enabled", nullable = false)
    private boolean autoOrchestratorEnabled = false;

    /**
     * Per-course override (in seconds) of the auto-orchestration debounce window. When {@code null} the global default from
     * {@code AtlasOrchestratorProperties#debounceWindowSeconds()} applies.
     */
    @Column(name = "debounce_window_seconds_override")
    private Integer debounceWindowSecondsOverride;

    /**
     * Per-course override of the daily auto-orchestration run cap. When {@code null} the global default from
     * {@code AtlasOrchestratorProperties#maxDailyOrchestrations()} applies.
     */
    @Column(name = "max_daily_orchestration_override")
    private Integer maxDailyOrchestrationOverride;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public boolean isGradeRelevant() {
        return gradeRelevant;
    }

    public void setGradeRelevant(boolean gradeRelevant) {
        this.gradeRelevant = gradeRelevant;
    }

    public boolean isDataRetentionHold() {
        return dataRetentionHold;
    }

    public void setDataRetentionHold(boolean dataRetentionHold) {
        this.dataRetentionHold = dataRetentionHold;
    }

    @JsonIgnore
    public ZonedDateTime getResetWarningSentDate() {
        return resetWarningSentDate;
    }

    public void setResetWarningSentDate(ZonedDateTime resetWarningSentDate) {
        this.resetWarningSentDate = resetWarningSentDate;
    }

    @JsonIgnore
    public ZonedDateTime getStudentDataResetDate() {
        return studentDataResetDate;
    }

    public void setStudentDataResetDate(ZonedDateTime studentDataResetDate) {
        this.studentDataResetDate = studentDataResetDate;
    }

    public boolean isAutoOrchestratorEnabled() {
        return autoOrchestratorEnabled;
    }

    public void setAutoOrchestratorEnabled(boolean autoOrchestratorEnabled) {
        this.autoOrchestratorEnabled = autoOrchestratorEnabled;
    }

    public Integer getDebounceWindowSecondsOverride() {
        return debounceWindowSecondsOverride;
    }

    public void setDebounceWindowSecondsOverride(Integer debounceWindowSecondsOverride) {
        this.debounceWindowSecondsOverride = debounceWindowSecondsOverride;
    }

    public Integer getMaxDailyOrchestrationOverride() {
        return maxDailyOrchestrationOverride;
    }

    public void setMaxDailyOrchestrationOverride(Integer maxDailyOrchestrationOverride) {
        this.maxDailyOrchestrationOverride = maxDailyOrchestrationOverride;
    }
}
