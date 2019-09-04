package de.tum.in.www1.artemis.domain;

import java.io.Serializable;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A ProgrammingExerciseOptions.
 */
@Entity
@Table(name = "programming_exercise_options")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ProgrammingExerciseOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @JsonIgnore
    private Long id;

    @Column(name = "automatic_submission_run_after_due_date")
    private boolean automaticSubmissionRunAfterDueDate;

    @MapsId
    @OneToOne
    @JoinColumn(name = "id")
    @JsonIgnoreProperties("programmingExerciseOptions")
    private ProgrammingExercise programmingExercise;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    public ProgrammingExerciseOptions programmingExercise(ProgrammingExercise exercise) {
        this.programmingExercise = exercise;
        return this;
    }

    public void setProgrammingExercise(ProgrammingExercise exercise) {
        this.programmingExercise = exercise;
    }

    public boolean isAutomaticSubmissionRunAfterDueDate() {
        return automaticSubmissionRunAfterDueDate;
    }

    public void setAutomaticSubmissionRunAfterDueDate(boolean automaticSubmissionRunAfterDueDate) {
        this.automaticSubmissionRunAfterDueDate = automaticSubmissionRunAfterDueDate;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProgrammingExerciseOptions)) {
            return false;
        }
        return id != null && id.equals(((ProgrammingExerciseOptions) o).id);
    }

    @Override
    public int hashCode() {
        return 31;
    }

    @Override
    public String toString() {
        return "ProgrammingExerciseOptions{" + "id=" + id + ", automaticSubmissionRunAfterDueDate=" + automaticSubmissionRunAfterDueDate + ", programmingExercise="
                + programmingExercise + '}';
    }
}
