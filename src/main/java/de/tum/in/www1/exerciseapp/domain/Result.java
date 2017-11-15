package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
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

    //TODO: we might want to store it as a list (see quizzes)
    @OneToMany(mappedBy = "result", cascade = CascadeType.REMOVE)
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Feedback> feedbacks = new HashSet<>();

    @ManyToOne
    private Participation participation;

    /**
     * This property stores the total number of results in the participation this result belongs to.
     * Not stored in the database, computed dynamically and used in showing statistics to the user
     * in the exercise view.
     */
    @Transient
    @JsonProperty
    private Long submissionCount;

    public Long getSubmissionCount() {
        return submissionCount;
    }

    public void setSubmissionCount(Long submissionCount) {
        this.submissionCount = submissionCount;
    }


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

    public Set<Feedback> getFeedbacks() {
        return feedbacks;
    }

    public Result feedbacks(Set<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
        return this;
    }

    public Result addFeedbacks(Feedback feedback) {
        this.feedbacks.add(feedback);
        feedback.setResult(this);
        return this;
    }

    public Result removeFeedbacks(Feedback feedback) {
        this.feedbacks.remove(feedback);
        feedback.setResult(null);
        return this;
    }

    public void setFeedbacks(Set<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    public void applyQuizSubmission(QuizSubmission quizSubmission) {
        QuizExercise quizExercise = (QuizExercise) getParticipation().getExercise();
        setScore(quizExercise.getScoreForSubmission(quizSubmission));
        setSuccessful(true); // TODO: Valentin: ask: What does "successful" mean in this context?
    }

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
