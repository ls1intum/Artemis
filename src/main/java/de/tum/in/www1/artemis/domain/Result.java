package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.math.BigDecimal.ROUND_HALF_EVEN;

/**
 * A Result.
 */
@Entity
@Table(name = "result")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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

    /**
     * Relative score in %
     */
    @Column(name = "score")
    @JsonView(QuizView.After.class)
    private Long score;

    /**
     * Describes whether a result counts against the total score of a student.
     * It determines whether the result is shown in the course dashboard or not.
     * For quiz exercises:
     * - results are rated=true when students participate in the live quiz mode (there can only be one such result)
     * - results are rated=false when students participate in the practice mode
     * <p>
     * For all other exercises (modeling, programming, etc.)
     * - results are rated=true when students submit before the due date (or when the due date is null),
     * multiple results can be rated=true, then the result with the last completionDate counts towards the total score of a student
     * - results are rated=false when students submit after the due date
     */
    @Column(name = "rated")
    @JsonView(QuizView.Before.class)
    private Boolean rated;

    // This explicit flag exists intentionally, as sometimes a Result is loaded from the database without
    // loading it's Feedback list. In this case you still want to know, if Feedback for this Result exists
    // without querying the server/database again.
    @Column(name = "hasFeedback")
    private Boolean hasFeedback;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    @JsonView(QuizView.Before.class)
    @JsonIgnoreProperties({"result", "participation"})
    private Submission submission;

    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JsonIgnoreProperties("result")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<Feedback> feedbacks = new ArrayList<>();

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private Participation participation;

    @OneToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @JoinColumn(unique = false)
    private User assessor;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type")
    @JsonView(QuizView.After.class)
    private AssessmentType assessmentType;

    @Column(name = "has_complaint")
    private Boolean hasComplaint;

    @Column(name = "example_result")
    private Boolean exampleResult;

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

    /**
     * builds and sets the resultString attribute
     *
     * @param totalScore total amount of scored points between 0 and maxScore
     * @param maxScore   maximum score reachable at corresponding exercise
     */
    public void setResultString(Double totalScore, @Nullable Double maxScore) {
        DecimalFormat formatter = new DecimalFormat("#.##");
        if (maxScore == null) {
            resultString = (formatter.format(totalScore) + " points");
        } else {
            resultString = (formatter.format(totalScore) + " of " + formatter.format(maxScore) + " points");
        }
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
        this.successful = score == 100L;
    }

    /**
     * calculates and sets the score attribute and accordingly the successful flag
     *
     * @param totalScore total amount of scored points between 0 and maxScore
     * @param maxScore   maximum score reachable at corresponding exercise
     */
    public void setScore(Double totalScore, @Nullable Double maxScore) {
        Long score = (maxScore == null) ? 100L : Math.round(totalScore / maxScore * 100);
        setScore(score);
    }

    public Boolean isRated() {
        return rated;
    }

    public Result rated(Boolean rated) {
        this.rated = rated;
        return this;
    }

    public void setRated(Boolean rated) {
        this.rated = rated;
    }

    public void setRatedIfNotExceeded(ZonedDateTime exerciseDueDate, ZonedDateTime submissionDate) {
        this.rated = exerciseDueDate == null || submissionDate.isBefore(exerciseDueDate);
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

    public Boolean hasComplaint() {
        return hasComplaint;
    }

    public Result hasComplaint(Boolean hasComplaint) {
        this.hasComplaint = hasComplaint;
        return this;
    }

    public void setHasComplaint(Boolean hasComplaint) {
        this.hasComplaint = hasComplaint;
    }

    public Boolean isExampleResult() {
        return exampleResult;
    }

    public Result exampleResult(Boolean exampleResult) {
        this.exampleResult = exampleResult;
        return this;
    }

    public void setExampleResult(Boolean exampleResult) {
        this.exampleResult = exampleResult;
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
            setResultString(quizExercise.getScoreInPointsForSubmission(quizSubmission), quizExercise.getMaxTotalScore().doubleValue());
        }
    }

    // TODO CZ: not necessary - AssessmentService#submitResult could be used for calculating the score and setting the result string for modeling exercises instead/as well
    public void evaluateFeedback(double maxScore) {
        double totalScore = calculateTotalScore();
        setScore(totalScore, maxScore);
        setResultString(totalScore, maxScore);
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

    /**
     * @return sum of every feedback credit rounded to max two numbers after the comma
     */
    // TODO CZ: not necessary - AssessmentService#submitResult could be used for calculating the score and setting the result string for modeling exercises instead/as well
    private double calculateTotalScore() {
        double totalScore = 0.0;
        for (Feedback feedback : this.feedbacks) {
            totalScore += feedback.getCredits();
        }
        return new BigDecimal(totalScore).setScale(2, ROUND_HALF_EVEN).doubleValue(); // TODO CZ: does ROUND_HALF_EVEN make sense here? why not use ROUND_HALF_UP?
    }
}
