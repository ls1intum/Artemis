package de.tum.in.www1.artemis.domain.participation;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;

/**
 * A TutorParticipation.
 */
@Entity
@Table(name = "tutor_participation")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorParticipation extends DomainObject {

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TutorParticipationStatus status;

    @ManyToOne
    private Exercise assessedExercise;

    @ManyToOne
    private User tutor;

    @ManyToMany
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JoinTable(name = "tutor_participation_trained_example_submissions", joinColumns = @JoinColumn(name = "tutor_participation_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "trained_example_submissions_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "tutorParticipations" })
    private Set<ExampleSubmission> trainedExampleSubmissions = new HashSet<>();
    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    public TutorParticipationStatus getStatus() {
        return status;
    }

    public TutorParticipation status(TutorParticipationStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(TutorParticipationStatus status) {
        this.status = status;
    }

    public Exercise getAssessedExercise() {
        return assessedExercise;
    }

    public TutorParticipation assessedExercise(Exercise exercise) {
        this.assessedExercise = exercise;
        return this;
    }

    public void setAssessedExercise(Exercise exercise) {
        this.assessedExercise = exercise;
    }

    public User getTutor() {
        return tutor;
    }

    public TutorParticipation tutor(User user) {
        this.tutor = user;
        return this;
    }

    public void setTutor(User user) {
        this.tutor = user;
    }

    public Set<ExampleSubmission> getTrainedExampleSubmissions() {
        return trainedExampleSubmissions;
    }

    public TutorParticipation trainedExampleSubmissions(Set<ExampleSubmission> exampleSubmissions) {
        this.trainedExampleSubmissions = exampleSubmissions;
        return this;
    }

    public TutorParticipation addTrainedExampleSubmissions(ExampleSubmission exampleSubmission) {
        this.trainedExampleSubmissions.add(exampleSubmission);
        exampleSubmission.getTutorParticipations().add(this);
        return this;
    }

    public TutorParticipation removeTrainedExampleSubmissions(ExampleSubmission exampleSubmission) {
        this.trainedExampleSubmissions.remove(exampleSubmission);
        exampleSubmission.getTutorParticipations().remove(this);
        return this;
    }

    public void setTrainedExampleSubmissions(Set<ExampleSubmission> exampleSubmissions) {
        this.trainedExampleSubmissions = exampleSubmissions;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public String toString() {
        return "TutorParticipation{" + "id=" + getId() + ", status='" + getStatus() + "}";
    }
}
