package de.tum.cit.aet.artemis.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "campus_online_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CampusOnlineConfiguration extends DomainObject {

    @OneToOne(mappedBy = "campusOnlineConfiguration")
    @JsonIgnore
    private Course course;

    @Column(name = "campus_online_course_id", nullable = false)
    private String campusOnlineCourseId;

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
}
