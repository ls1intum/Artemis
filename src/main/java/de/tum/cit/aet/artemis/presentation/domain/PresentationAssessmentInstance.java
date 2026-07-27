package de.tum.cit.aet.artemis.presentation.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A scheduled and graded occurrence of a presentation assessment.
 */
@Entity
@Table(name = "presentation_assessment_instance")
public class PresentationAssessmentInstance extends DomainObject {

    public static final String ENTITY_NAME = "presentationAssessmentInstance";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PresentationAssessment presentationAssessment;

    @Column(name = "presentation_date")
    private ZonedDateTime presentationDate;

    @Column(name = "result_points")
    private Double resultPoints;

    @Column(name = "language", length = 10)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "presentation_mode", length = 20)
    private PresentationAssessmentMode mode;

    @Column(name = "location")
    private String location;

    @Column(name = "meeting_link", length = 1000)
    private String meetingLink;

    @ManyToMany
    @JoinTable(name = "presentation_assessment_instance_student", joinColumns = @JoinColumn(name = "presentation_assessment_instance_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "student_id", referencedColumnName = "id"))
    private Set<User> students = new HashSet<>();

    public PresentationAssessment getPresentationAssessment() {
        return presentationAssessment;
    }

    public void setPresentationAssessment(PresentationAssessment presentationAssessment) {
        this.presentationAssessment = presentationAssessment;
    }

    public ZonedDateTime getPresentationDate() {
        return presentationDate;
    }

    public void setPresentationDate(ZonedDateTime presentationDate) {
        this.presentationDate = presentationDate;
    }

    public Double getResultPoints() {
        return resultPoints;
    }

    public void setResultPoints(Double resultPoints) {
        this.resultPoints = resultPoints;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public PresentationAssessmentMode getMode() {
        return mode;
    }

    public void setMode(PresentationAssessmentMode mode) {
        this.mode = mode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getMeetingLink() {
        return meetingLink;
    }

    public void setMeetingLink(String meetingLink) {
        this.meetingLink = meetingLink;
    }

    public Set<User> getStudents() {
        return students;
    }

    public void setStudents(Set<User> students) {
        this.students = students;
    }
}
