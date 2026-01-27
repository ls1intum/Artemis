package de.tum.cit.aet.artemis.assessment.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * An ExampleParticipation represents a participation used for tutor training with example submissions.
 * It extends Participation to ensure all submissions have a valid participation reference,
 * enabling NOT NULL constraints on submission.participation_id.
 *
 * ExampleParticipation-specific fields are stored in a secondary table (example_participation_details)
 * to avoid bloating the main participation table.
 */
@Entity
@DiscriminatorValue("EP")
@SecondaryTable(name = "example_participation_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExampleParticipation extends Participation {

    @Column(name = "used_for_tutorial", table = "example_participation_details")
    private Boolean usedForTutorial;

    @Column(name = "assessment_explanation", table = "example_participation_details", length = 2000)
    private String assessmentExplanation;

    // Note: The relationship with TutorParticipation will be added when TutorParticipation is migrated
    // to use trainedExampleParticipations instead of trainedExampleSubmissions
    @Transient
    private Set<TutorParticipation> tutorParticipations = new HashSet<>();

    public boolean isUsedForTutorial() {
        return Boolean.TRUE.equals(usedForTutorial);
    }

    public Boolean getUsedForTutorial() {
        return usedForTutorial;
    }

    public void setUsedForTutorial(Boolean usedForTutorial) {
        this.usedForTutorial = usedForTutorial;
    }

    public String getAssessmentExplanation() {
        return assessmentExplanation;
    }

    public ExampleParticipation assessmentExplanation(String assessmentExplanation) {
        this.assessmentExplanation = assessmentExplanation;
        return this;
    }

    public void setAssessmentExplanation(String assessmentExplanation) {
        this.assessmentExplanation = assessmentExplanation;
    }

    public Set<TutorParticipation> getTutorParticipations() {
        return tutorParticipations;
    }

    public void setTutorParticipations(Set<TutorParticipation> tutorParticipations) {
        this.tutorParticipations = tutorParticipations;
    }

    public void addTutorParticipation(TutorParticipation tutorParticipation) {
        this.tutorParticipations.add(tutorParticipation);
        // Note: The bidirectional relationship will be managed when TutorParticipation
        // is migrated to use trainedExampleParticipations
    }

    public void removeTutorParticipation(TutorParticipation tutorParticipation) {
        this.tutorParticipations.remove(tutorParticipation);
        // Note: The bidirectional relationship will be managed when TutorParticipation
        // is migrated to use trainedExampleParticipations
    }

    /**
     * Get the submission for this example participation.
     * ExampleParticipation typically has exactly one submission.
     *
     * @return the submission or null if none exists
     */
    @JsonIgnore
    public Submission getSubmission() {
        Set<Submission> submissions = getSubmissions();
        if (submissions == null || submissions.isEmpty()) {
            return null;
        }
        return submissions.iterator().next();
    }

    @Override
    public Exercise getExercise() {
        return exercise;
    }

    public ExampleParticipation exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    @Override
    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public String getType() {
        return "example";
    }

    @Override
    public void filterSensitiveInformation() {
        // No sensitive information to filter for example participations
    }

    @Override
    public String toString() {
        return "ExampleParticipation{" + "id=" + getId() + ", usedForTutorial=" + isUsedForTutorial() + "}";
    }
}
