package de.tum.in.www1.artemis.domain.participation;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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
public class TutorParticipation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TutorParticipationStatus status;

    @Column(name = "points")
    private Integer points;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Integer getPoints() {
        return points;
    }

    public TutorParticipation points(Integer points) {
        this.points = points;
        return this;
    }

    public void setPoints(Integer points) {
        this.points = points;
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TutorParticipation tutorParticipation = (TutorParticipation) o;
        if (tutorParticipation.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), tutorParticipation.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "TutorParticipation{" + "id=" + getId() + ", status='" + getStatus() + "'" + ", points=" + getPoints() + "}";
    }
}
