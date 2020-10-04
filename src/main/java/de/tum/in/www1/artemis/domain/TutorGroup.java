package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.Weekday;

/**
 * A TutorGroup.
 */
@Entity
@Table(name = "tutor_group")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorGroup extends DomainObject {

    @Column(name = "name")
    private String name;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "weekday")
    private Weekday weekday;

    @Column(name = "time_slot")
    private String timeSlot;

    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    private Language language;

    @Column(name = "room")
    private String room;

    @ManyToOne
    @JsonIgnoreProperties("tutorGroups")
    private User tutor;

    @ManyToMany
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JoinTable(name = "tutor_group_students", joinColumns = @JoinColumn(name = "tutor_group_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "students_id", referencedColumnName = "id"))
    private Set<User> students = new HashSet<>();

    @ManyToOne
    @JsonIgnoreProperties("tutorGroups")
    private Course course;

    public String getName() {
        return name;
    }

    public TutorGroup name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public TutorGroup capacity(Integer capacity) {
        this.capacity = capacity;
        return this;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Weekday getWeekday() {
        return weekday;
    }

    public TutorGroup weekday(Weekday weekday) {
        this.weekday = weekday;
        return this;
    }

    public void setWeekday(Weekday weekday) {
        this.weekday = weekday;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public TutorGroup timeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
        return this;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public Language getLanguage() {
        return language;
    }

    public TutorGroup language(Language language) {
        this.language = language;
        return this;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getRoom() {
        return room;
    }

    public TutorGroup room(String room) {
        this.room = room;
        return this;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public User getTutor() {
        return tutor;
    }

    public TutorGroup tutor(User user) {
        this.tutor = user;
        return this;
    }

    public void setTutor(User user) {
        this.tutor = user;
    }

    public Set<User> getStudents() {
        return students;
    }

    public TutorGroup students(Set<User> users) {
        this.students = users;
        return this;
    }

    public TutorGroup addStudents(User user) {
        this.students.add(user);
        return this;
    }

    public TutorGroup removeStudents(User user) {
        this.students.remove(user);
        return this;
    }

    public void setStudents(Set<User> users) {
        this.students = users;
    }

    public Course getCourse() {
        return course;
    }

    public TutorGroup course(Course course) {
        this.course = course;
        return this;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    @Override
    public String toString() {
        return "TutorGroup{" + "id=" + getId() + ", name='" + getName() + "'" + ", capacity=" + getCapacity() + ", weekday='" + getWeekday() + "'" + ", timeSlot='" + getTimeSlot()
                + "'" + ", language='" + getLanguage() + "'" + ", room='" + getRoom() + "'" + "}";
    }
}
