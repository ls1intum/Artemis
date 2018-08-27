package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

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
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "submitted")
    @JsonView(QuizView.Before.class)
    private Boolean submitted;

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    @JsonView(QuizView.Before.class)
    private SubmissionType type;

    @ManyToOne
    private Participation participation;

    @OneToOne(mappedBy = "submission", cascade=CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval=true)
    @JsonIgnoreProperties({"submission", "participation"})
    @JoinColumn(unique = true)
    private Result result;

    @Column(name = "submission_date")
    private ZonedDateTime submissionDate;

    @JsonView(QuizView.Before.class)
    public ZonedDateTime getSubmissionDate() {
        return submissionDate;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Participation getParticipation() {
        return participation;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
    }

    public Submission submissionDate(ZonedDateTime submissionDate) {
        this.submissionDate = submissionDate;
        return this;
    }

    public void setSubmissionDate(ZonedDateTime submissionDate) {
        this.submissionDate = submissionDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean isSubmitted() {
        return submitted != null ? submitted : false;
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
