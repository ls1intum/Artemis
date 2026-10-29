package de.tum.cit.aet.artemis.hyperion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * Persistence entity that stores the Hyperion course level settings payload.
 * The payload is mapped as JSON so the structure can evolve without schema changes.
 */
@Entity
@Table(name = "hyperion_course_settings")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class HyperionCourseSettingsEntity {

    @Id
    @Column(name = "course_id", nullable = false)
    @Parent
    private Long courseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false, columnDefinition = "json")
    private HyperionCourseSettings settings = HyperionCourseSettings.defaultSettings();

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public HyperionCourseSettings getSettings() {
        return settings;
    }

    public void setSettings(HyperionCourseSettings settings) {
        this.settings = settings == null ? HyperionCourseSettings.defaultSettings() : settings;
    }
}
