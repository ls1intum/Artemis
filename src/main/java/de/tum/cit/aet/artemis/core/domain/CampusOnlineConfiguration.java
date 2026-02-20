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
 * JPA entity representing the CAMPUSOnline integration configuration for a single Artemis course.
 * <p>
 * Each Artemis course can optionally be linked to a CAMPUSOnline course through this entity,
 * which stores the external course ID and optional metadata (instructor, department, study program).
 * The relationship is a one-to-one mapping owned by the {@link Course} entity.
 */
@Entity
@Table(name = "campus_online_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CampusOnlineConfiguration extends DomainObject {

    /** The Artemis course this configuration belongs to (inverse side of the relationship). */
    @OneToOne(mappedBy = "campusOnlineConfiguration")
    @JsonIgnore
    private Course course;

    /** The unique course identifier in the CAMPUSOnline system. */
    @Column(name = "campus_online_course_id", nullable = false)
    private String campusOnlineCourseId;

    /** The name of the responsible instructor as stored in CAMPUSOnline. */
    @Column(name = "responsible_instructor")
    private String responsibleInstructor;

    /** The department or faculty that owns the course in CAMPUSOnline. */
    @Column(name = "department")
    private String department;

    /** The study program associated with this course in CAMPUSOnline. */
    @Column(name = "study_program")
    private String studyProgram;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getCampusOnlineCourseId() {
        return campusOnlineCourseId;
    }

    public void setCampusOnlineCourseId(String campusOnlineCourseId) {
        this.campusOnlineCourseId = campusOnlineCourseId;
    }

    public String getResponsibleInstructor() {
        return responsibleInstructor;
    }

    public void setResponsibleInstructor(String responsibleInstructor) {
        this.responsibleInstructor = responsibleInstructor;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getStudyProgram() {
        return studyProgram;
    }

    public void setStudyProgram(String studyProgram) {
        this.studyProgram = studyProgram;
    }
}
