package de.tum.in.www1.artemis.domain.tutorialgroups;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import de.tum.in.www1.artemis.domain.*;
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
    @Size(min = 1, max = 256)
    @NotNull
    private String title;

    @Column(name = "additional_information")
    @Lob
    private String additionalInformation;

    @Column(name = "capacity")
    @Min(1)
    private Integer capacity;

    @Column(name = "is_online")
    @NotNull
    private Boolean isOnline = false;

    @Column(name = "campus")
    @Size(min = 1, max = 256)
    private String campus;

    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    private Language language;

    @ManyToOne
    @JoinColumn(name = "teaching_assistant_id")
    private User teachingAssistant;

    @OneToMany(mappedBy = "tutorialGroup", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties(value = "tutorialGroup", allowSetters = true)
    private Set<TutorialGroupRegistration> registrations = new HashSet<>();

    // ==== Transient fields ====

    @Transient
    @JsonSerialize
    private Boolean isUserRegistered;

    @Transient
    @JsonSerialize
    private Integer numberOfRegisteredUsers;

    public TutorialGroup() {
        // Empty constructor needed for Jackson.
    }

    public TutorialGroup(Course course, String title, String additionalInformation, Integer capacity, Boolean isOnline, String campus, Language language, User teachingAssistant,
            Set<TutorialGroupRegistration> registrations) {
        this.course = course;
        this.title = title;
        this.additionalInformation = additionalInformation;
        this.capacity = capacity;
        this.isOnline = isOnline;
        this.campus = campus;
        this.language = language;
        this.teachingAssistant = teachingAssistant;
        this.registrations = registrations;
    }

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

    public String getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(String additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Boolean getIsOnline() {
        return isOnline;
    }

    public void setIsOnline(Boolean online) {
        isOnline = online;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public User getTeachingAssistant() {
        return teachingAssistant;
    }

    public void setTeachingAssistant(User teachingAssistant) {
        this.teachingAssistant = teachingAssistant;
    }

    public Set<TutorialGroupRegistration> getRegistrations() {
        return registrations;
    }

    public void setRegistrations(Set<TutorialGroupRegistration> registrations) {
        this.registrations = registrations;
    }

    public String getCampus() {
        return campus;
    }

    public void setCampus(String campus) {
        this.campus = campus;
    }

    public Boolean getIsUserRegistered() {
        return isUserRegistered;
    }

    public void setIsUserRegistered(Boolean userRegistered) {
        isUserRegistered = userRegistered;
    }

    public Integer getNumberOfRegisteredUsers() {
        return numberOfRegisteredUsers;
    }

    public void setNumberOfRegisteredUsers(Integer numberOfRegisteredUsers) {
        this.numberOfRegisteredUsers = numberOfRegisteredUsers;
    }

    /**
     * Sets the transient fields isUserRegistered and numberOfRegisteredUsers for the given user.
     *
     * @param user the user for which the transient fields should be set
     */
    public void setTransientPropertiesForUser(User user) {
        if (Hibernate.isInitialized(registrations) && registrations != null) {
            this.setIsUserRegistered(registrations.stream().anyMatch(registration -> registration.getStudent().equals(user)));
            this.setNumberOfRegisteredUsers(registrations.size());
        }
        else {
            this.setIsUserRegistered(null);
            this.setNumberOfRegisteredUsers(null);
        }
    }

    /**
     * Hides privacy sensitive information.
     */
    public void hidePrivacySensitiveInformation() {
        this.setRegistrations(null);
    }

}
