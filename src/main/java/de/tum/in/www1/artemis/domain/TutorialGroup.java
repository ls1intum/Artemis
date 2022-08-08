package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.Language;

@Entity
@Table(name = "tutorial_group")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroup extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "title")
    @Size(max = 256)
    @NotBlank()
    private String title;

    @Column(name = "additional_information")
    @Size(max = 2000)
    @Lob
    private String additionalInformation;

    @Column(name = "capacity")
    @Min(1)
    private Integer capacity;

    @Column(name = "is_online")
    @NotNull
    private Boolean isOnline = false;

    @Column(name = "location")
    @Size(max = 2000)
    @Lob
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    private Language language;

    @ManyToOne
    @JoinColumn(name = "teaching_assistant_id")
    @NotNull
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

    public String getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(String additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Boolean getIsOnline() {
        return isOnline;
    }

    public void setIsOnline(Boolean online) {
        isOnline = online;
    }
}
