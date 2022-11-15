package de.tum.in.www1.artemis.domain.tutorialgroups;

import java.util.*;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

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
    @Size(min = 1, max = 19)
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

    /**
     * This transient field is set to true if the user who requested the entity is registered for this tutorial group
     */
    @Transient
    @JsonSerialize
    private Boolean isUserRegistered;

    /**
     * This transient field is set to true if the user who requested the entity is the teaching assistant of this tutorial group
     */
    @Transient
    @JsonSerialize
    private Boolean isUserTutor;

    /**
     * This transient fields is set to the number of registered students for this tutorial group
     */
    @Transient
    @JsonSerialize
    private Integer numberOfRegisteredUsers;

    /**
     * This transient fields is set to the name of the teaching assistant of this tutorial group
     */
    @Transient
    @JsonSerialize
    private String teachingAssistantName;

    /**
     * This transient fields is set to the course title to which this tutorial group belongs
     */
    @Transient
    @JsonSerialize
    private String courseTitle;

    /**
     * This transient field is set to the next session of this tutorial group
     */
    @Transient
    @JsonSerialize
    private TutorialGroupSession nextSession;

    @OneToOne(mappedBy = "tutorialGroup", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties(value = "tutorialGroup", allowSetters = true)
    private TutorialGroupSchedule tutorialGroupSchedule;

    @OneToMany(mappedBy = "tutorialGroup", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "tutorialGroup, tutorialGroupSchedule", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<TutorialGroupSession> tutorialGroupSessions = new HashSet<>();

    public TutorialGroupSchedule getTutorialGroupSchedule() {
        return tutorialGroupSchedule;
    }

    public void setTutorialGroupSchedule(TutorialGroupSchedule tutorialGroupSchedule) {
        this.tutorialGroupSchedule = tutorialGroupSchedule;
    }

    public Set<TutorialGroupSession> getTutorialGroupSessions() {
        return tutorialGroupSessions;
    }

    public void setTutorialGroupSessions(Set<TutorialGroupSession> tutorialGroupSessions) {
        this.tutorialGroupSessions = tutorialGroupSessions;
    }

    public TutorialGroup() {
        // Empty constructor needed for Jackson.
    }

    public TutorialGroup(Course course, String title) {
        this.course = course;
        this.title = title;
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

    public Boolean getIsUserTutor() {
        return isUserTutor;
    }

    public void setIsUserTutor(Boolean userTutor) {
        isUserTutor = userTutor;
    }

    public Integer getNumberOfRegisteredUsers() {
        return numberOfRegisteredUsers;
    }

    public void setNumberOfRegisteredUsers(Integer numberOfRegisteredUsers) {
        this.numberOfRegisteredUsers = numberOfRegisteredUsers;
    }

    public String getTeachingAssistantName() {
        return teachingAssistantName;
    }

    public void setTeachingAssistantName(String teachingAssistantName) {
        this.teachingAssistantName = teachingAssistantName;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public TutorialGroupSession getNextSession() {
        return nextSession;
    }

    public void setNextSession(TutorialGroupSession nextSession) {
        this.nextSession = nextSession;
    }

    /**
     * Hides privacy sensitive information.
     */
    public void hidePrivacySensitiveInformation() {
        this.setRegistrations(null);
        this.setTeachingAssistant(null);
        this.setCourse(null);
    }

    /**
     * Removes circular references for JSON serialization.
     *
     * @param tutorialGroup the tutorial group to remove circular references for
     * @return the tutorial group without circular references
     */
    public static TutorialGroup preventCircularJsonConversion(TutorialGroup tutorialGroup) {

        // prevent circular to json conversion
        if (Persistence.getPersistenceUtil().isLoaded(tutorialGroup, "tutorialGroupSchedule") && tutorialGroup.getTutorialGroupSchedule() != null) {
            tutorialGroup.getTutorialGroupSchedule().setTutorialGroup(null);
        }
        if (Persistence.getPersistenceUtil().isLoaded(tutorialGroup, "tutorialGroupSessions") && tutorialGroup.getTutorialGroupSessions() != null) {
            tutorialGroup.getTutorialGroupSessions().forEach(tutorialGroupSession -> {
                tutorialGroupSession.setTutorialGroup(null);
                if (tutorialGroupSession.getTutorialGroupSchedule() != null) {
                    tutorialGroupSession.getTutorialGroupSchedule().setTutorialGroup(null);
                    tutorialGroupSession.getTutorialGroupSchedule().setTutorialGroupSessions(null);
                }
            });
        }
        return tutorialGroup;
    }

}
