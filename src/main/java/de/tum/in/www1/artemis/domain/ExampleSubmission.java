package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * A ExampleSubmission.
 */
@Entity
@Table(name = "example_submission")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ExampleSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "used_for_tutorial")
    private Boolean usedForTutorial;

    @ManyToOne
    private Exercise exercise;

    @OneToOne
    @JoinColumn(unique = true)
    private Submission submission;

    @ManyToOne
    @JsonIgnoreProperties("trainedExampleSubmissions")
    private TutorParticipation tutorParticipation;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean isUsedForTutorial() {
        return usedForTutorial;
    }

    public ExampleSubmission usedForTutorial(Boolean usedForTutorial) {
        this.usedForTutorial = usedForTutorial;
        return this;
    }

    public void setUsedForTutorial(Boolean usedForTutorial) {
        this.usedForTutorial = usedForTutorial;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public ExampleSubmission exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Submission getSubmission() {
        return submission;
    }

    public ExampleSubmission submission(Submission submission) {
        this.submission = submission;
        return this;
    }

    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    public TutorParticipation getTutorParticipation() {
        return tutorParticipation;
    }

    public ExampleSubmission tutorParticipation(TutorParticipation tutorParticipation) {
        this.tutorParticipation = tutorParticipation;
        return this;
    }

    public void setTutorParticipation(TutorParticipation tutorParticipation) {
        this.tutorParticipation = tutorParticipation;
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
        ExampleSubmission exampleSubmission = (ExampleSubmission) o;
        if (exampleSubmission.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), exampleSubmission.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ExampleSubmission{" +
            "id=" + getId() +
            ", usedForTutorial='" + isUsedForTutorial() + "'" +
            "}";
    }
}
