package de.tum.in.www1.artemis.domain;

import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * A TutorParticipation.
 */
@Entity
@Table(name = "tutor_participation")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
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

    @OneToOne
    @JoinColumn(unique = true)
    private Exercise assessedExercise;

    @OneToOne
    @JoinColumn(unique = true)
    private User tutor;

    @OneToMany(mappedBy = "tutorParticipation")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
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
        exampleSubmission.setTutorParticipation(this);
        return this;
    }

    public TutorParticipation removeTrainedExampleSubmissions(ExampleSubmission exampleSubmission) {
        this.trainedExampleSubmissions.remove(exampleSubmission);
        exampleSubmission.setTutorParticipation(null);
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
        return "TutorParticipation{" +
            "id=" + getId() +
            ", status='" + getStatus() + "'" +
            ", points=" + getPoints() +
            "}";
    }
}
