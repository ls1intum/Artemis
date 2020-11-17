package de.tum.in.www1.artemis.domain;

import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.base.Strings;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A Result.
 */
@Entity
@Table(name = "result")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Result extends DomainObject {

    @Column(name = "result_string")
    @JsonView(QuizView.After.class)
    private String resultString;

    @Column(name = "completion_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime completionDate;

    @Column(name = "jhi_successful")
    @JsonView(QuizView.After.class)
    private Boolean successful;

    /**
     * Relative score in %
     */
    @Column(name = "score")
    @JsonView(QuizView.After.class)
    private Long score;

    /**
     * Describes whether a result counts against the total score of a student. It determines whether the result is shown in the course dashboard or not. For quiz exercises: -
     * results are rated=true when students participate in the live quiz mode (there can only be one such result) - results are rated=false when students participate in the
     * practice mode
     * <p>
     * For all other exercises (modeling, programming, etc.) - results are rated=true when students submit before the due date (or when the due date is null), multiple results can
     * be rated=true, then the result with the last completionDate counts towards the total score of a student - results are rated=false when students submit after the due date
     */
    @Column(name = "rated")
    @JsonView(QuizView.Before.class)
    private Boolean rated;

    // This explicit flag exists intentionally, as sometimes a Result is loaded from the database without
    // loading it's Feedback list. In this case you still want to know, if Feedback for this Result exists
    // without querying the server/database again.
    // IMPORTANT: Please note, that this flag should only be used for Programming Exercises at the moment
    // all other exercise types should set this flag to false
    @Column(name = "hasFeedback")
    private Boolean hasFeedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonView(QuizView.Before.class)
    @JsonIgnoreProperties({ "result", "participation" })
    private Submission submission;

    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JsonIgnoreProperties(value = "result", allowSetters = true)
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
     * Sets the resultString attribute
     *
     * @param totalScore total amount of scored points between 0 and maxScore
     * @param maxScore   maximum score reachable at corresponding exercise
     */
    public void setResultString(Double totalScore, @Nullable Double maxScore) {
        resultString = createResultString(totalScore, maxScore);
    }

    /**
     * Builds the resultString attribute
     *
     * @param totalScore total amount of scored points
     * @param maxScore   maximum score reachable at corresponding exercise
     * @return String with result string in this format "2 of 13 points" or "2 points"
     */
    public String createResultString(Double totalScore, @Nullable Double maxScore) {
        DecimalFormat formatter = new DecimalFormat("#.##");
        if (maxScore == null) {
            return formatter.format(totalScore) + " points";
        }
        else {
            return formatter.format(totalScore) + " of " + formatter.format(maxScore) + " points";
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

    public Long getScore() {
        return score;
    }

    public Result score(Long score) {
        this.score = score;
        return this;
    }

    /**
     * This explicit flag exists intentionally, as sometimes a Result is loaded from the database without loading it's Feedback list. In this case you still want to know, if
     * Feedback for this Result exists without querying the server/database again. IMPORTANT: Please note, that this flag should only be used for Programming Exercises at the
     * moment all other exercise types should set this flag to false
     *
     * @param hasFeedback explicit flag used only by Programming Exercise
     */
    public void setHasFeedback(Boolean hasFeedback) {
        this.hasFeedback = hasFeedback;
    }

    /**
     * This explicit flag exists intentionally, as sometimes a Result is loaded from the database without loading it's Feedback list. In this case you still want to know, if
     * Feedback for this Result exists without querying the server/database again. IMPORTANT: Please note, that this flag should only be used for Programming Exercises at the
     * moment all other exercise types should set this flag to false
     *
     * @return true if the result has feedback, otherwise false
     */
    public Boolean getHasFeedback() {
        return hasFeedback;
    }

    /**
     * This explicit flag exists intentionally, as sometimes a Result is loaded from the database without loading it's Feedback list. In this case you still want to know, if
     * Feedback for this Result exists without querying the server/database again. IMPORTANT: Please note, that this flag should only be used for Programming Exercises at the
     * moment all other exercise types should set this flag to false
     *
     * @param hasFeedback explicit flag used only by Programming Exercise
     * @return result with newly set hasFeedback property
     */
    public Result hasFeedback(Boolean hasFeedback) {
        this.hasFeedback = hasFeedback;
        return this;
    }

    /**
     * 1. set score 2. set successful = true, if score >= 100 or false if not
     *
     * @param score new score
     */
    public void setScore(Long score) {
        if (score != null) {
            this.score = score;
            this.successful = score >= 100L;
        }
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

    public void setRatedIfNotExceeded(@Nullable ZonedDateTime exerciseDueDate, ZonedDateTime submissionDate) {
        this.rated = exerciseDueDate == null || submissionDate.isBefore(exerciseDueDate);
    }

    /**
     * A result is rated if:
     * - the submission date is before the due date OR
     * - no due date is set OR
     * - the submission type is INSTRUCTOR / TEST
     * @param exerciseDueDate date after which no normal submission is considered rated.
     * @param submission to which the result belongs.
     */
    public void setRatedIfNotExceeded(ZonedDateTime exerciseDueDate, Submission submission) {
        if (submission.getType() == SubmissionType.INSTRUCTOR || submission.getType() == SubmissionType.TEST) {
            this.rated = true;
        }
        else {
            setRatedIfNotExceeded(exerciseDueDate, submission.getSubmissionDate());
        }
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

    public Result addFeedbacks(List<Feedback> feedbacks) {
        feedbacks.forEach(this::addFeedback);
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

    /**
     * Assigns the given feedback list to the result. It first sets the positive flag and the feedback type of every feedback element, clears the existing list of feedback and
     * assigns the new feedback afterwards. IMPORTANT: This method should not be used for Quiz and Programming exercises with completely automatic assessments!
     *
     * @param feedbacks the new feedback list
     * @param skipAutomaticResults if true automatic results won't be updated
     */
    public void updateAllFeedbackItems(List<Feedback> feedbacks, boolean skipAutomaticResults) {
        for (Feedback feedback : feedbacks) {
            if (skipAutomaticResults && feedback.getType() == FeedbackType.AUTOMATIC) {
                continue;
            }
            if (feedback.getCredits() != null) {
                feedback.setPositive(feedback.getCredits() >= 0);
            }
            setFeedbackType(feedback);
        }
        // Note: If there is old feedback that gets removed here and not added again in the forEach-loop, it
        // will also be deleted in the database because of the 'orphanRemoval = true' flag.
        getFeedbacks().clear();
        feedbacks.forEach(this::addFeedback);
    }

    /**
     * Sets the feedback type of a new feedback element. The type is set to MANUAL if it was not set before. It is set to AUTOMATIC_ADAPTED if Compass created the feedback
     * automatically and the tutor has overridden the feedback in the manual assessment. This is done to differentiate between automatic feedback that was overridden manually and
     * pure manual feedback to analyze the quality of automatic assessments. In all other cases the type stays the same.
     *
     * @param feedback the new feedback for which to set the type
     */
    private void setFeedbackType(Feedback feedback) {
        if (feedback.getType() == null) {
            feedback.setType(FeedbackType.MANUAL);
        }
        else if ((feedback.getType().equals(FeedbackType.AUTOMATIC) && feedbackHasChanged(feedback))) {
            feedback.setType(FeedbackType.AUTOMATIC_ADAPTED);
        }
    }

    /**
     * Checks for a new feedback if the score or text has changed compared to the already existing feedback for the same element.
     */
    private boolean feedbackHasChanged(Feedback feedback) {
        if (this.feedbacks == null || this.feedbacks.size() == 0) {
            return false;
        }
        return this.feedbacks.stream().filter(existingFeedback -> existingFeedback.getReference() != null && existingFeedback.getReference().equals(feedback.getReference()))
                .anyMatch(sameFeedback -> !sameFeedback.getCredits().equals(feedback.getCredits()) || feedbackTextHasChanged(sameFeedback.getText(), feedback.getText()));
    }

    /**
     * Compares the given feedback texts (existingText and newText) and checks if the text has changed.
     */
    private boolean feedbackTextHasChanged(String existingText, String newText) {
        if (Strings.isNullOrEmpty(existingText) && Strings.isNullOrEmpty(newText)) {
            return false;
        }
        return !Objects.equals(existingText, newText);
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

    public void determineAssessmentType() {
        setAssessmentType(AssessmentType.MANUAL);
        if (feedbacks.stream().anyMatch(feedback -> feedback.getType() == FeedbackType.AUTOMATIC || feedback.getType() == FeedbackType.AUTOMATIC_ADAPTED)) {
            setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        }
    }

    public Boolean hasComplaint() {
        return hasComplaint;
    }

    /**
     * `hasComplaint` could be null in the database
     * @return hasComplaint property value
     */
    public Optional<Boolean> getHasComplaint() {
        return Optional.ofNullable(hasComplaint);
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
            StudentParticipation studentParticipation = (StudentParticipation) getParticipation();
            QuizExercise quizExercise = (QuizExercise) studentParticipation.getExercise();
            // update score
            setScore(quizExercise.getScoreForSubmission(quizSubmission));
            // update result string
            setResultString(quizExercise.getScoreInPointsForSubmission(quizSubmission), quizExercise.getMaxTotalScore().doubleValue());
        }
    }

    /**
     * Removes the assessor from the result, can be invoked to make sure that sensitive information is not sent to the client. E.g. students should not see information about
     * their assessor.
     */
    public void filterSensitiveInformation() {
        setAssessor(null);
    }

    /**
     * Checks whether the result is a manual result. A manual result can be from type MANUAL or SEMI_AUTOMATIC
     *
     * @return true if the result is a manual result
     */
    @JsonIgnore
    public boolean isManualResult() {
        return assessmentType == AssessmentType.MANUAL || assessmentType == AssessmentType.SEMI_AUTOMATIC;
    }

    @Override
    public String toString() {
        return "Result{" + "id=" + getId() + ", resultString='" + resultString + '\'' + ", completionDate=" + completionDate + ", successful=" + successful + ", score=" + score
                + ", rated=" + rated + ", hasFeedback=" + hasFeedback + ", assessmentType=" + assessmentType + ", hasComplaint=" + hasComplaint + '}';
    }
}
