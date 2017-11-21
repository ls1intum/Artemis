package de.tum.in.www1.exerciseapp.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

import de.tum.in.www1.exerciseapp.domain.enumeration.SubmissionType;

/**
 * A Submission.
 */
@Entity
@Table(name = "submission")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
    name = "discriminator",
    discriminatorType = DiscriminatorType.STRING
)
@DiscriminatorValue(value = "S")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public abstract class Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submitted")
    private Boolean submitted;

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    private SubmissionType type;

    @Transient
    // variable name must be different from Getter name,
    // so that Jackson ignores the @Transient annotation,
    // but Hibernate still respects it
    private ZonedDateTime submissionDateTransient;

    public ZonedDateTime getSubmissionDate() {
        return submissionDateTransient;
    }

    public void setSubmissionDate(ZonedDateTime submissionDate) {
        submissionDateTransient = submissionDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean isSubmitted() {
        return submitted;
    }

    public Submission submitted(Boolean submitted) {
        this.submitted = submitted;
        return this;
    }

    public void setSubmitted(Boolean submitted) {
        this.submitted = submitted;
    }

    public SubmissionType getType() {
        return type;
    }

    public Submission type(SubmissionType type) {
        this.type = type;
        return this;
    }

    public void setType(SubmissionType type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Submission submission = (Submission) o;
        if (submission.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), submission.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Submission{" +
            "id=" + getId() +
            ", submitted='" + isSubmitted() + "'" +
            ", type='" + getType() + "'" +
            "}";
    }
}
