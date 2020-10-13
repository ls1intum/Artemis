package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.artemis.domain.participation.Participation;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;

/**
 * A Submission.
 */
@Entity
@Table(name = "submission")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submitted")
    private Boolean submitted;

    @Column(name = "submission_date")
    private ZonedDateTime submissionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private SubmissionType type;

    @Column(name = "example_submission")
    private Boolean exampleSubmission;

    @OneToMany(mappedBy = "submission")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<Result> results = new HashSet<>();

    @ManyToOne
    @JsonIgnoreProperties(value = "submissions", allowSetters = true)
    private Participation participation;

    // jhipster-needle-entity-add-field - JHipster will add fields here
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

    public ZonedDateTime getSubmissionDate() {
        return submissionDate;
    }

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long durationInMinutes;

    /**
     * Calculates the duration of a submission in minutes
     *
     * @return duration in minutes or null if it can not be determined
     */
    public Long getDurationInMinutes() {
        if (this.participation == null || this.participation.getInitializationDate() == null || this.submissionDate == null) {
            return null;
        }

        ZonedDateTime initilizationDate = this.participation.getInitializationDate();
        ZonedDateTime submissionDate = this.getSubmissionDate();

        return Duration.between(initilizationDate, submissionDate).toMinutes();
    }

    public Result getResult() {
        return results.iterator().next();
    }

    public void setResult(Result result) {
        if (result != null) {
            this.results.add(result);
        }
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

    public Boolean isExampleSubmission() {
        return exampleSubmission;
    }

    public Submission exampleSubmission(Boolean exampleSubmission) {
        this.exampleSubmission = exampleSubmission;
        return this;
    }

    public void setExampleSubmission(Boolean exampleSubmission) {
        this.exampleSubmission = exampleSubmission;
    }

    public Set<Result> getResults() {
        return results;
    }

    public Submission results(Set<Result> exerciseResults) {
        this.results = exerciseResults;
        return this;
    }

    public Submission addResults(Result exerciseResults) {
        this.results.add(exerciseResults);
        exerciseResults.setSubmission(this);
        return this;
    }

    public Submission removeResults(Result exerciseResults) {
        this.results.remove(exerciseResults);
        exerciseResults.setSubmission(null);
        return this;
    }

    public void setResults(Set<Result> exerciseResults) {
        this.results = exerciseResults;
    }


    public Submission participation(Participation participation) {
        this.participation = participation;
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Submission)) {
            return false;
        }
        return id != null && id.equals(((Submission) o).id);
    }

    @Override
    public int hashCode() {
        return 31;
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Submission{" +
            "id=" + getId() +
            ", submitted='" + isSubmitted() + "'" +
            ", submissionDate='" + getSubmissionDate() + "'" +
            ", type='" + getType() + "'" +
            ", exampleSubmission='" + isExampleSubmission() + "'" +
            "}";
    }
}
