package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "tutorial_group")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroup extends DomainObject {

    @Column(name = "title")
    private String title;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne
    @JoinColumn(name = "teaching_assistant_id")
    private User teachingAssistant;

    @ManyToMany
    @JoinTable(name = "tutorial_group_registered_student", joinColumns = @JoinColumn(name = "tutorial_group_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "registered_student_id", referencedColumnName = "id"))
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("registeredTutorialGroups")
    private Set<User> registeredStudents = new HashSet<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public User getTeachingAssistant() {
        return teachingAssistant;
    }

    public void setTeachingAssistant(User teachingAssistant) {
        this.teachingAssistant = teachingAssistant;
    }

    public Set<User> getRegisteredStudents() {
        return registeredStudents;
    }

    public void setRegisteredStudents(Set<User> registeredStudents) {
        this.registeredStudents = registeredStudents;
    }
}
