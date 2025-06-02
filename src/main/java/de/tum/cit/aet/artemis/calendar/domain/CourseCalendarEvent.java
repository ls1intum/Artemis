package de.tum.cit.aet.artemis.calendar.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "course_calendar_event")
public class CourseCalendarEvent extends DomainObject implements Comparable<CourseCalendarEvent> {

    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    @JsonIgnoreProperties({ "courseCalendarEvents" })
    private Course course;

    @Column(name = "title")
    @NotNull
    private String title;

    @Column(name = "start_date")
    @NotNull
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    @Column(name = "location")
    private String location;

    @Column(name = "facilitator")
    private String facilitator;

    @Column(name = "visible_to_students")
    private boolean visibleToStudents;

    @Column(name = "visible_to_tutors")
    private boolean visibleToTutors;

    @Column(name = "visible_to_editors")
    private boolean visibleToEditors;

    @Column(name = "visible_to_instructors")
    private boolean visibleToInstructors;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getFacilitator() {
        return facilitator;
    }

    public void setFacilitator(String facilitator) {
        this.facilitator = facilitator;
    }

    public boolean isVisibleToStudents() {
        return visibleToStudents;
    }

    public void setVisibleToStudents(boolean visibleToStudents) {
        this.visibleToStudents = visibleToStudents;
    }

    public boolean isVisibleToTutors() {
        return visibleToTutors;
    }

    public void setVisibleToTutors(boolean visibleToTutors) {
        this.visibleToTutors = visibleToTutors;
    }

    public boolean isVisibleToEditors() {
        return visibleToEditors;
    }

    public void setVisibleToEditors(boolean visibleToEditors) {
        this.visibleToEditors = visibleToEditors;
    }

    public boolean isVisibleToInstructors() {
        return visibleToInstructors;
    }

    public void setVisibleToInstructors(boolean visibleToInstructors) {
        this.visibleToInstructors = visibleToInstructors;
    }

    @Override
    public int compareTo(CourseCalendarEvent o) {
        return startDate.compareTo(o.startDate);
    }
}
