package de.tum.cit.aet.artemis.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Configuration entity for extended course settings that are rarely needed.
 * <p>
 * This entity is lazily loaded from the Course entity to reduce database load
 * when courses are frequently fetched but these extended settings are rarely needed.
 * <p>
 * Contains:
 * - Course description (potentially large text)
 * - Messaging code of conduct (typically ~2000 characters)
 * - Course archive path (only relevant for archived courses)
 */
@Entity
@Table(name = "course_extended_settings")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseExtendedSettings extends DomainObject {

    @OneToOne(mappedBy = "extendedSettings")
    @JsonIgnore
    private Course course;

    @Column(name = "description")
    private String description;

    @Column(name = "messaging_code_of_conduct")
    private String messagingCodeOfConduct;

    @Column(name = "course_archive_path")
    private String courseArchivePath;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMessagingCodeOfConduct() {
        return messagingCodeOfConduct;
    }

    public void setMessagingCodeOfConduct(String messagingCodeOfConduct) {
        this.messagingCodeOfConduct = messagingCodeOfConduct;
    }

    public String getCourseArchivePath() {
        return courseArchivePath;
    }

    public void setCourseArchivePath(String courseArchivePath) {
        this.courseArchivePath = courseArchivePath;
    }

    /**
     * @return true if the course has been archived (has an archive path)
     */
    @JsonIgnore
    public boolean hasCourseArchive() {
        return courseArchivePath != null && !courseArchivePath.isEmpty();
    }
}
