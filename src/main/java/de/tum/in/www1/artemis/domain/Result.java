package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
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
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "result_string")
    @JsonView(QuizView.After.class)
    private String resultString;

    @Column(name = "completion_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime completionDate;

    @Column(name = "jhi_successful")
    @JsonView(QuizView.After.class)
    private Boolean successful;

    @Column(name = "build_artifact")
    @JsonView(QuizView.Before.class)
    private Boolean buildArtifact;

    @Column(name = "score")
    @JsonView(QuizView.After.class)
    private Long score;

    @Column(name = "rated")
    @JsonView(QuizView.Before.class)
    private Boolean rated;

    @Column(name = "hasFeedback")
    private Boolean hasFeedback;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    @JsonView(QuizView.Before.class)
    @JsonIgnoreProperties({"result", "participation"})
    private Submission submission;

    @OneToMany(mappedBy = "result", cascade = CascadeType.REMOVE)
    @OrderColumn
    @JsonIgnoreProperties("result")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<Feedback> feedbacks = new ArrayList<>();

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private Participation participation;

    @OneToOne(cascade=CascadeType.MERGE, fetch = FetchType.LAZY)
    @JoinColumn(unique = false)
    private User assessor;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type")
    @JsonView(QuizView.After.class)
    private AssessmentType assessmentType;

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

    public void setHasFeedback(Boolean hasFeedback) {
        this.hasFeedback = hasFeedback;
    }

    public Boolean getHasFeedback() {
        return hasFeedback;
    }

    public Result hasFeedback(Boolean hasFeedback) {
        this.hasFeedback = hasFeedback;
        return this;
    }

    /**
     * 1. set score
     * 2. set successful = true, if score is 100 or false if not
     *
     * @param score new score
     */
    public void setScore(Long score) {
        this.score = score;
        if (score == null) {
            this.successful = false;
        }
        else {
            //if score is 100 set successful true, if not, set it false
            successful = score == 100;
        }
    }

    public Boolean isRated() {
        return rated != null ? rated : false;
    }

    public Result rated(Boolean rated) {
        this.rated = rated;
        return this;
    }

    public void setRated(Boolean rated) {
        this.rated = rated;
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

    public List<Feedback> getFeedbacks() {
        return feedbacks;
    }

    public Result feedbacks(List<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
        return this;
    }

    public Result addFeedback(Feedback feedback) {
        this.feedbacks.add(feedback);
        feedback.setResult(this);
        return this;
    }

    public Result removeFeedback(Feedback feedback) {
        this.feedbacks.remove(feedback);
        feedback.setResult(null);
        return this;
    }

    public void setFeedbacks(List<Feedback> feedbacks) {
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

    public User getAssessor() {
        return assessor;
    }

    public Result assessor(User assessor) {
        this.assessor = assessor;
        return this;
    }

    public void setAssessor(User assessor) {
        this.assessor = assessor;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public Result assessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
        return this;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    /**
     * Updates the attributes "score" and "successful" by evaluating its submission
     */
    public void evaluateSubmission() {
        if (submission instanceof QuizSubmission) {
            QuizSubmission quizSubmission = (QuizSubmission) submission;
            // get the exercise this result belongs to
            QuizExercise quizExercise = (QuizExercise) getParticipation().getExercise();
            // update score
            setScore(quizExercise.getScoreForSubmission(quizSubmission));
            // update result string
            DecimalFormat formatter = new DecimalFormat("#.##"); // limit decimal places to 2
            setResultString(formatter.format(quizExercise.getScoreInPointsForSubmission(quizSubmission)) + " of " + formatter.format(quizExercise.getMaxTotalScore()) + " points");
            // update successful
            setSuccessful(score == 100L);
        }
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
            ", score=" + getScore() +
            ", rated='" + isRated() + "'" +
            ", hasFeedback='" + getHasFeedback() + "'" +
            "}";
    }
}
