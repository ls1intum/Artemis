package de.tum.in.www1.exerciseapp.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.io.Serializable;
import java.util.Objects;

/**
 * A ModelingSubmission.
 */
@Entity
@DiscriminatorValue(value="M")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ModelingSubmission extends Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "submission_path")
    private String submissionPath;

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove
    public String getSubmissionPath() {
        return submissionPath;
    }

    public ModelingSubmission submissionPath(String submissionPath) {
        this.submissionPath = submissionPath;
        return this;
    }

    public void setSubmissionPath(String submissionPath) {
        this.submissionPath = submissionPath;
    }
    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ModelingSubmission modelingSubmission = (ModelingSubmission) o;
        if (modelingSubmission.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), modelingSubmission.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ModelingSubmission{" +
            "id=" + getId() +
            ", submissionPath='" + getSubmissionPath() + "'" +
            "}";
    }
}
