package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A Result.
 */
@Entity
@Table(name = "result")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Result implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "result_string")
    private String resultString;

    @Column(name = "completion_date")
    private ZonedDateTime completionDate;

    @Column(name = "jhi_successful")
    private Boolean successful;

    @Column(name = "build_artifact")
    private Boolean buildArtifact;

    @Column(name = "score")
    private Long score;

    @OneToOne
    @JoinColumn(unique = true)
    private Submission submission;

    @ManyToOne
    private Participation participation;

    // number of results in the participation this result belongs to
    @Transient
    @JsonProperty
    private Long submissionCount;

    public Long getSubmissionCount() {
        return submissionCount;
    }

    public void setSubmissionCount(Long submissionCount) {
        this.submissionCount = submissionCount;
    }


    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResultString() {
        return resultString;
    }

    public Result resultString(String resultString) {
        this.resultString = resultString;
        return this;
    }

    public void setResultString(String resultString) {
        this.resultString = resultString;
    }

    public ZonedDateTime getCompletionDate() {
        return completionDate;
    }

    public Result completionDate(ZonedDateTime completionDate) {
        this.completionDate = completionDate;
        return this;
    }

    public void setCompletionDate(ZonedDateTime completionDate) {
        this.completionDate = completionDate;
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public Result successful(Boolean successful) {
        this.successful = successful;
        return this;
    }

    public void setSuccessful(Boolean successful) {
        this.successful = successful;
    }

    public Boolean isBuildArtifact() {
        return buildArtifact;
    }

    public Result buildArtifact(Boolean buildArtifact) {
        this.buildArtifact = buildArtifact;
        return this;
    }

    public void setBuildArtifact(Boolean buildArtifact) {
        this.buildArtifact = buildArtifact;
    }

    public Long getScore() {
        return score;
    }

    public Result score(Long score) {
        this.score = score;
        return this;
    }

    public void setScore(Long score) {
        this.score = score;
    }

    public Submission getSubmission() {
        return submission;
    }

    public Result submission(Submission submission) {
        this.submission = submission;
        return this;
    }

    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    public Participation getParticipation() {
        return participation;
    }

    public Result participation(Participation participation) {
        this.participation = participation;
        return this;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
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
        Result result = (Result) o;
        if (result.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), result.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Result{" +
            "id=" + getId() +
            ", resultString='" + getResultString() + "'" +
            ", completionDate='" + getCompletionDate() + "'" +
            ", successful='" + isSuccessful() + "'" +
            ", buildArtifact='" + isBuildArtifact() + "'" +
            ", score='" + getScore() + "'" +
            "}";
    }
}
