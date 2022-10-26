package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.SIZE_OF_UNSIGNED_TINYINT;
import static de.tum.in.www1.artemis.service.util.RoundingUtil.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.*;
import com.google.common.base.Strings;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.hestia.CoverageFileReport;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.service.listeners.ResultListener;

/**
 * A Result.
 */
@Entity
@Table(name = "result")
@EntityListeners({ AuditingEntityListener.class, ResultListener.class })
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Result extends DomainObject {

    @Column(name = "completion_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime completionDate;

    @Column(name = "jhi_successful")
    @JsonView(QuizView.After.class)
    private Boolean successful;

    /**
     * Relative score in % (typically between 0 ... 100, can also be larger if bonus points are available)
     */
    @Column(name = "score")
    @JsonView(QuizView.After.class)
    private Double score;

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
    // loading its Feedback list. In this case you still want to know if Feedback for this Result exists
    // without querying the server/database again.
    // IMPORTANT: Please note, that this flag should only be used for Programming Exercises at the moment
    // all other exercise types should set this flag to false
    @Column(name = "has_feedback")
    private Boolean hasFeedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonView(QuizView.Before.class)
    @JsonIgnoreProperties({ "results", "participation" })
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn()
    private User assessor;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type")
    @JsonView(QuizView.After.class)
    private AssessmentType assessmentType;

    @Column(name = "has_complaint")
    private Boolean hasComplaint;

    @Column(name = "example_result")
    private Boolean exampleResult;

    // The following attributes are only used for Programming Exercises
    @Column(name = "test_case_count")
    private Integer testCaseCount = 0;

    @Column(name = "passed_test_case_count")
    private Integer passedTestCaseCount = 0;

    @Column(name = "code_issue_count")
    private Integer codeIssueCount = 0;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    @JsonIgnore
    private Instant lastModifiedDate;

    // This attribute is required to forward the coverage file reports after creating the build result. This is required in order to
    // delay referencing the corresponding test cases from the entries because the test cases are not saved in the database
    // at this point of time but the required test case name would be lost, otherwise.
    @Transient
    @JsonIgnore
    private Map<String, Set<CoverageFileReport>> fileReportsByTestCaseName;

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

    public Double getScore() {
        return score;
    }

    public Result score(Double score) {
        this.score = score;
        return this;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    /**
     * This explicit flag exists intentionally, as sometimes a Result is loaded from the database without loading its Feedback list. In this case you still want to know, if
     * Feedback for this Result exists without querying the server/database again. IMPORTANT: Please note, that this flag should only be used for Programming Exercises at the
     * moment all other exercise types should set this flag to false
     *
     * @param hasFeedback explicit flag used only by Programming Exercise
     */
    public void setHasFeedback(Boolean hasFeedback) {
        this.hasFeedback = hasFeedback;
    }

    /**
     * This explicit flag exists intentionally, as sometimes a Result is loaded from the database without loading its Feedback list. In this case you still want to know, if
     * Feedback for this Result exists without querying the server/database again. IMPORTANT: Please note, that this flag should only be used for Programming Exercises at the
     * moment all other exercise types should set this flag to false
     *
     * @return true if the result has feedback, otherwise false
     */
    public Boolean getHasFeedback() {
        return hasFeedback;
    }

    /**
     * This explicit flag exists intentionally, as sometimes a Result is loaded from the database without loading its Feedback list. In this case you still want to know, if
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
     * Sets the score to the specified score rounded to 4 decimal places.
     * If you are handling student results that potentially need rounding, use {@link Result#setScore(Double score, Course course)} instead!
     *
     * @param score new score
     */
    public void setScore(Double score) {
        if (score != null) {
            // We need to round the score to four decimal places to have a score of 99.999999 to be rounded to 100.0.
            // Otherwise, a result would not be successful.
            this.score = roundToNDecimalPlaces(score, 4);
            this.successful = this.score >= 100.0;
        }
    }

    /**
     * 1. set score and round it to the specified accuracy in the course
     * 2. set successful = true, if score >= 100 or false if not
     *
     * @param score new score
     * @param course the course that specifies the accuracy
     */
    public void setScore(Double score, Course course) {
        if (score != null) {
            setScore(roundScoreSpecifiedByCourseSettings(score, course));
        }
    }

    /**
     * calculates and sets the score attribute and accordingly the successful flag
     *
     * @param totalPoints total amount of points between 0 and maxPoints
     * @param maxPoints   maximum points reachable at corresponding exercise
     * @param course the course that specifies the accuracy
     */
    public void setScore(double totalPoints, double maxPoints, Course course) {
        setScore(totalPoints / maxPoints * 100, course);
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
        this.rated = exerciseDueDate == null || submissionDate.isBefore(exerciseDueDate) || submissionDate.isEqual(exerciseDueDate);
    }

    /**
     * A result is rated if:
     * - the submission date is before the due date OR
     * - no due date is set OR
     * - the submission type is INSTRUCTOR / TEST
     * @param exerciseDueDate date after which no normal submission is considered rated.
     * @param submission to which the result belongs.
     * @param participation to wich the submission belongs
     */
    public void setRatedIfNotExceeded(ZonedDateTime exerciseDueDate, Submission submission, Participation participation) {
        if (submission.getType() == SubmissionType.INSTRUCTOR || submission.getType() == SubmissionType.TEST) {
            this.rated = true;
        }
        else if (submission.getType() == SubmissionType.ILLEGAL || participation.isTestRun()) {
            this.rated = false;
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

    public void addFeedbacks(List<Feedback> feedbacks) {
        feedbacks.forEach(this::addFeedback);
    }

    public void removeFeedback(Feedback feedback) {
        this.feedbacks.remove(feedback);
        feedback.setResult(null);
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
                feedback.setPositiveViaCredits();
            }
            else {
                feedback.setCredits(0.0);
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
        if (this.feedbacks == null || this.feedbacks.isEmpty()) {
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

    public Integer getTestCaseCount() {
        return testCaseCount;
    }

    public void setTestCaseCount(int testCaseCount) {
        this.testCaseCount = Math.min(testCaseCount, SIZE_OF_UNSIGNED_TINYINT);
    }

    public Integer getPassedTestCaseCount() {
        return passedTestCaseCount;
    }

    public void setPassedTestCaseCount(int passedTestCaseCount) {
        this.passedTestCaseCount = Math.min(passedTestCaseCount, SIZE_OF_UNSIGNED_TINYINT);
    }

    public Integer getCodeIssueCount() {
        return codeIssueCount;
    }

    public void setCodeIssueCount(int codeIssueCount) {
        this.codeIssueCount = Math.min(codeIssueCount, SIZE_OF_UNSIGNED_TINYINT);
    }

    public Map<String, Set<CoverageFileReport>> getCoverageFileReportsByTestCaseName() {
        return fileReportsByTestCaseName;
    }

    public void setCoverageFileReportsByTestCaseName(Map<String, Set<CoverageFileReport>> fileReportsByTestCaseName) {
        this.fileReportsByTestCaseName = fileReportsByTestCaseName;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    /**
     * Updates the attributes "score" and "successful" by evaluating its submission.
     * <b>Important</b>: the quizSubmission has to be loaded with eager submitted answers, otherwise this method will not work correctly
     */
    public void evaluateQuizSubmission() {
        if (submission instanceof QuizSubmission quizSubmission) {
            // get the exercise this result belongs to
            StudentParticipation studentParticipation = (StudentParticipation) getParticipation();
            QuizExercise quizExercise = (QuizExercise) studentParticipation.getExercise();
            // update score
            setScore(quizExercise.getScoreForSubmission(quizSubmission), quizExercise.getCourseViaExerciseGroupOrCourseMember());
        }
    }

    /**
     * Removes the assessor from the result, can be invoked to make sure that sensitive information is not sent to the client. E.g. students should not see information about
     * their assessor.
     *
     * Does not filter feedbacks.
     */
    public void filterSensitiveInformation() {
        setAssessor(null);
    }

    /**
     * Remove all feedbacks marked with visibility never.
     * @param isBeforeDueDate if feedbacks marked with visibility 'after due date' should also be removed.
     */
    public void filterSensitiveFeedbacks(boolean isBeforeDueDate) {
        feedbacks.removeIf(Feedback::isInvisible);

        if (isBeforeDueDate) {
            feedbacks.removeIf(Feedback::isAfterDueDate);
        }
        setTestCaseCount((int) feedbacks.stream().filter(Feedback::isTestFeedback).count());
        setPassedTestCaseCount((int) feedbacks.stream().filter(Feedback::isTestFeedback).filter(Feedback::isPositive).count());
    }

    /**
     * Checks whether the result is a manual result. A manual result can be from type MANUAL or SEMI_AUTOMATIC
     *
     * @return true if the result is a manual result
     */
    @JsonIgnore
    public boolean isManual() {
        return AssessmentType.MANUAL == assessmentType || AssessmentType.SEMI_AUTOMATIC == assessmentType;
    }

    /**
     * Checks whether the result is an automatic result: AUTOMATIC
     *
     * @return true if the result is an automatic result
     */
    @JsonIgnore
    public boolean isAutomatic() {
        return AssessmentType.AUTOMATIC == assessmentType;
    }

    @Override
    public String toString() {
        return "Result{" + "id" + getId() + ", completionDate=" + completionDate + ", successful=" + successful + ", score=" + score + ", rated=" + rated + ", hasFeedback="
                + hasFeedback + ", assessmentType=" + assessmentType + ", hasComplaint=" + hasComplaint + ", testCaseCount=" + testCaseCount + ", passedTestCaseCount="
                + passedTestCaseCount + ", codeIssueCount=" + codeIssueCount + '}';
    }

    /**
     * Calculates the total score for programming exercises. Do not use it for other exercise types
     * @return calculated totalScore
     */
    public Double calculateTotalPointsForProgrammingExercises() {
        double totalPoints = 0.0;
        double scoreAutomaticTests = 0.0;
        ProgrammingExercise programmingExercise = (ProgrammingExercise) getParticipation().getExercise();
        List<Feedback> feedbacks = getFeedbacks();
        var gradingInstructions = new HashMap<Long, Integer>(); // { instructionId: noOfEncounters }

        for (Feedback feedback : feedbacks) {
            if (feedback.getGradingInstruction() != null) {
                totalPoints = feedback.computeTotalScore(totalPoints, gradingInstructions);
            }
            else {
                /*
                 * In case no structured grading instruction was applied on the assessment model we just sum the feedback credit. We differentiate between automatic test and
                 * automatic SCA feedback (automatic test feedback has to be capped)
                 */
                if (feedback.getType() == FeedbackType.AUTOMATIC && !feedback.isStaticCodeAnalysisFeedback()) {
                    scoreAutomaticTests += Objects.requireNonNullElse(feedback.getCredits(), 0.0);
                }
                else {
                    totalPoints += Objects.requireNonNullElse(feedback.getCredits(), 0.0);
                }
            }
        }
        /*
         * Calculated score from automatic test feedbacks, is capped to max points + bonus points, see also see {@link ProgrammingExerciseGradingService#updateScore}
         */
        double maxPoints = programmingExercise.getMaxPoints() + Objects.requireNonNullElse(programmingExercise.getBonusPoints(), 0.0);
        if (scoreAutomaticTests > maxPoints) {
            scoreAutomaticTests = maxPoints;
        }
        totalPoints += scoreAutomaticTests;
        // Make sure to not give negative points
        if (totalPoints < 0) {
            totalPoints = 0;
        }
        // Make sure to not give more than maxPoints
        if (totalPoints > maxPoints) {
            totalPoints = maxPoints;
        }
        return totalPoints;
    }

    /**
     * calculates the score for programming exercises
     * @param exercise the exercise
     */
    public void calculateScoreForProgrammingExercise(ProgrammingExercise exercise) {
        double totalPoints = calculateTotalPointsForProgrammingExercises();
        setScore(totalPoints, exercise.getMaxPoints(), exercise.getCourseViaExerciseGroupOrCourseMember());
    }

    /**
     * Copies the relevant counters for programming exercises i.e. amount of (passed) test cases and code issues into this result
     * @param originalResult the source for the values
     */
    public void copyProgrammingExerciseCounters(Result originalResult) {
        setTestCaseCount(originalResult.getTestCaseCount());
        setPassedTestCaseCount(originalResult.getPassedTestCaseCount());
        setCodeIssueCount(originalResult.getCodeIssueCount());
    }
}
