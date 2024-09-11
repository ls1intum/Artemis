package de.tum.cit.aet.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;

/**
 * A ExampleSubmission.
 */
@Entity
@Table(name = "example_submission")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExampleSubmission extends DomainObject {

    @Column(name = "used_for_tutorial")
    private Boolean usedForTutorial;

    @ManyToOne
    private Exercise exercise;

    @OneToOne(cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JoinColumn(unique = true)
    private Submission submission;

    @ManyToMany(mappedBy = "trainedExampleSubmissions")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties({ "trainedExampleSubmissions", "assessedExercise" })
    private Set<TutorParticipation> tutorParticipations = new HashSet<>();

    @Column(name = "assessment_explanation")
    private String assessmentExplanation;

    public boolean isUsedForTutorial() {
        return Boolean.TRUE.equals(usedForTutorial);
    }

    public void setUsedForTutorial(Boolean usedForTutorial) {
        this.usedForTutorial = usedForTutorial;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Submission getSubmission() {
        return submission;
    }

    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    public Set<TutorParticipation> getTutorParticipations() {
        return tutorParticipations;
    }

    public void addTutorParticipations(TutorParticipation tutorParticipation) {
        this.tutorParticipations.add(tutorParticipation);
        tutorParticipation.getTrainedExampleSubmissions().add(this);
    }

    public void removeTutorParticipations(TutorParticipation tutorParticipation) {
        this.tutorParticipations.remove(tutorParticipation);
        tutorParticipation.getTrainedExampleSubmissions().remove(this);
    }

    public void setTutorParticipations(Set<TutorParticipation> tutorParticipations) {
        this.tutorParticipations = tutorParticipations;
    }

    public String getAssessmentExplanation() {
        return assessmentExplanation;
    }

    public ExampleSubmission assessmentExplanation(String assessmentExplanation) {
        this.assessmentExplanation = assessmentExplanation;
        return this;
    }

    public void setAssessmentExplanation(String assessmentExplanation) {
        this.assessmentExplanation = assessmentExplanation;
    }

    @Override
    public String toString() {
        return "ExampleSubmission{" + "id=" + getId() + ", usedForTutorial='" + isUsedForTutorial() + "'" + "}";
    }
}
