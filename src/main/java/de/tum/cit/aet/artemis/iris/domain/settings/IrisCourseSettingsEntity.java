package de.tum.cit.aet.artemis.iris.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Persistence entity that stores the Iris course level settings payload.
 * The payload is mapped as JSON to allow evolving the structure without schema changes.
 */
@Entity
@Table(name = "course_iris_settings")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCourseSettingsEntity {

    @Id
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false, columnDefinition = "json")
    private IrisCourseSettings settings = IrisCourseSettings.defaultSettings();

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public IrisCourseSettings getSettings() {
        return settings;
    }

    public void setSettings(IrisCourseSettings settings) {
        this.settings = settings == null ? IrisCourseSettings.defaultSettings() : settings;
    }
}
