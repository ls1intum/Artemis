package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;

/**
 * A ExerciseResult.
 */
@Entity
@Table(name = "result")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ExerciseResult implements Serializable {

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

    @Column(name = "rated")
    private Boolean rated;

    @Column(name = "has_feedback")
    private Boolean hasFeedback;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type")
    private AssessmentType assessmentType;

    @OneToOne    @JoinColumn(unique = true)
    private User assessor;

    @OneToMany(mappedBy = "result")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Feedback> feedbacks = new HashSet<>();
    @OneToOne(mappedBy = "result")
    @JsonIgnore
    private Submission submission;

    @ManyToOne
    @JsonIgnoreProperties("results")
    private Participation participation;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResultString() {
        return resultString;
    }

    public ExerciseResult resultString(String resultString) {
        this.resultString = resultString;
        return this;
    }

    public void setResultString(String resultString) {
        this.resultString = resultString;
    }

    public ZonedDateTime getCompletionDate() {
        return completionDate;
    }

    public ExerciseResult completionDate(ZonedDateTime completionDate) {
        this.completionDate = completionDate;
        return this;
    }

    public void setCompletionDate(ZonedDateTime completionDate) {
        this.completionDate = completionDate;
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public ExerciseResult successful(Boolean successful) {
        this.successful = successful;
        return this;
    }

    public void setSuccessful(Boolean successful) {
        this.successful = successful;
    }

    public Boolean isBuildArtifact() {
        return buildArtifact;
    }

    public ExerciseResult buildArtifact(Boolean buildArtifact) {
        this.buildArtifact = buildArtifact;
        return this;
    }

    public void setBuildArtifact(Boolean buildArtifact) {
        this.buildArtifact = buildArtifact;
    }

    public Long getScore() {
        return score;
    }

    public ExerciseResult score(Long score) {
        this.score = score;
        return this;
    }

    public void setScore(Long score) {
        this.score = score;
    }

    public Boolean isRated() {
        return rated;
    }

    public ExerciseResult rated(Boolean rated) {
        this.rated = rated;
        return this;
    }

    public void setRated(Boolean rated) {
        this.rated = rated;
    }

    public Boolean isHasFeedback() {
        return hasFeedback;
    }

    public ExerciseResult hasFeedback(Boolean hasFeedback) {
        this.hasFeedback = hasFeedback;
        return this;
    }

    public void setHasFeedback(Boolean hasFeedback) {
        this.hasFeedback = hasFeedback;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public ExerciseResult assessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
        return this;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    public User getAssessor() {
        return assessor;
    }

    public ExerciseResult assessor(User user) {
        this.assessor = user;
        return this;
    }

    public void setAssessor(User user) {
        this.assessor = user;
    }

    public Set<Feedback> getFeedbacks() {
        return feedbacks;
    }

    public ExerciseResult feedbacks(Set<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
        return this;
    }

    public ExerciseResult addFeedbacks(Feedback feedback) {
        this.feedbacks.add(feedback);
        feedback.setResult(this);
        return this;
    }

    public ExerciseResult removeFeedbacks(Feedback feedback) {
        this.feedbacks.remove(feedback);
        feedback.setResult(null);
        return this;
    }

    public void setFeedbacks(Set<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
    }

    public Submission getSubmission() {
        return submission;
    }

    public ExerciseResult submission(Submission submission) {
        this.submission = submission;
        return this;
    }

    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    public Participation getParticipation() {
        return participation;
    }

    public ExerciseResult participation(Participation participation) {
        this.participation = participation;
        return this;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
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
        ExerciseResult exerciseResult = (ExerciseResult) o;
        if (exerciseResult.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), exerciseResult.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ExerciseResult{" +
            "id=" + getId() +
            ", resultString='" + getResultString() + "'" +
            ", completionDate='" + getCompletionDate() + "'" +
            ", successful='" + isSuccessful() + "'" +
            ", buildArtifact='" + isBuildArtifact() + "'" +
            ", score=" + getScore() +
            ", rated='" + isRated() + "'" +
            ", hasFeedback='" + isHasFeedback() + "'" +
            ", assessmentType='" + getAssessmentType() + "'" +
            "}";
    }
}
