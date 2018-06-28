package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A ModelingSubmission.
 */
@Entity
@Table(name = "modeling_submission")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ModelingSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_path")
    private String submissionPath;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

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
