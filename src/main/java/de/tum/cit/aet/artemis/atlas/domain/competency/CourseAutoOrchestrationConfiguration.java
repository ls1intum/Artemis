package de.tum.cit.aet.artemis.atlas.domain.competency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Per-course configuration for the Atlas auto-orchestration pipeline, stored in its own table rather
 * than as columns on {@code Course} so the (Atlas-owned) configuration does not widen the core course
 * entity and is only loaded when actually needed. Mirrors the separate-table pattern used by
 * {@code OnlineCourseConfiguration} (LTI) and {@code course_iris_settings} (Iris).
 * <p>
 * A row exists only for courses that have customized the configuration (enabled the pipeline or set
 * an override); a course with no row behaves as fully default — pipeline disabled, global debounce /
 * daily-cap defaults from {@code AtlasOrchestratorProperties}.
 */
@Entity
@Table(name = "course_auto_orchestration_configuration")
public class CourseAutoOrchestrationConfiguration extends DomainObject {

    /**
     * Owning {@link Course}. The relationship is owned by the course side ({@code Course} holds the
     * {@code auto_orchestration_configuration_id} foreign key), so this back-reference is
     * {@code mappedBy} and ignored during serialization to avoid a cycle.
     */
    @OneToOne(mappedBy = "autoOrchestrationConfiguration")
    @JsonIgnore
    private Course course;

    /**
     * Hard per-course kill switch. Even with the global {@code Feature.AtlasAgent} toggle on, a course
     * only participates in the debounce / scheduler pipeline when this flag is set.
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    /**
     * Per-course override (in seconds) of the auto-orchestration debounce window. When {@code null}
     * the global default from {@code AtlasOrchestratorProperties#debounceWindowSeconds()} applies.
     */
    @Column(name = "debounce_window_seconds_override")
    private Integer debounceWindowSecondsOverride;

    /**
     * Per-course override of the daily auto-orchestration run cap. When {@code null} the global
     * default from {@code AtlasOrchestratorProperties#maxDailyOrchestrations()} applies.
     */
    @Column(name = "max_daily_orchestration_override")
    private Integer maxDailyOrchestrationOverride;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
