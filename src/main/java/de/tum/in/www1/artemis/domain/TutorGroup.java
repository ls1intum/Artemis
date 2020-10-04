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

    public void setName(String name) {
        this.name = name;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Weekday getWeekday() {
        return weekday;
    }

    public void setWeekday(Weekday weekday) {
        this.weekday = weekday;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public User getTutor() {
        return tutor;
    }

    public void setTutor(User user) {
        this.tutor = user;
    }

    public Set<User> getStudents() {
        return students;
    }

    public void setStudents(Set<User> users) {
        this.students = users;
    }

    public Course getCourse() {
        return course;
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
