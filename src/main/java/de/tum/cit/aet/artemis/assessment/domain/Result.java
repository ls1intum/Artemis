package de.tum.cit.aet.artemis.assessment.domain;

import static de.tum.cit.aet.artemis.core.config.Constants.PROGRAMMING_GRACE_PERIOD_SECONDS;
import static de.tum.cit.aet.artemis.core.config.Constants.SIZE_OF_UNSIGNED_TINYINT;
import static de.tum.cit.aet.artemis.core.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;
import static de.tum.cit.aet.artemis.core.util.RoundingUtil.roundToNDecimalPlaces;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.ResultListener;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;

/**
 * A Result.
 */
@Entity
@Table(name = "result")
@EntityListeners({ AuditingEntityListener.class, ResultListener.class })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Result extends DomainObject implements Comparable<Result> {

    @Column(name = "completion_date")
    private ZonedDateTime completionDate;

    @Column(name = "jhi_successful")
    private Boolean successful;

    /**
     * Relative score in % (typically between 0 ... 100, can also be larger if bonus points are available)
     */
    @Column(name = "score")
    private Double score;

    /**
     * Describes whether a result counts against the total score of a student. It determines whether the result is shown in the course dashboard or not. For quiz exercises: -
     * results are rated=true when students participate in the live quiz mode (there can only be one such result) - results are rated=false when students participate in the
     * practice mode
     * <p>
     * For all other exercises (modeling, programming, etc.) - results are rated=true when students submit before the due date (or when the due date is null), multiple results can
     * be rated=true, then the result with the last completionDate counts towards the total score of a student - results are rated=false when students submit after the due date
     */
    // TODO: we should change this to a primitive boolean in the future with default value false
    @Nullable
    @Column(name = "rated")
    private Boolean rated;

    @ManyToOne
    @JsonIgnoreProperties({ "results" })
    @JoinColumn(nullable = false)
    private Submission submission;

    // Stored as a Set: feedback ordering is not semantically meaningful — every consumer that cares about
    // presentation order sorts explicitly (by credits, by FeedbackType, by reference, ...). Using a Set
    // avoids the @OrderColumn null-index race (Hibernate "Illegal null value for list index" under
    // concurrent multi-node assessment writes) and avoids the MultipleBagFetchException that an unordered
    // List would trigger together with the assessmentNote bag.
    // HashSet is intentional — Hibernate7Module's REPLACE_PERSISTENT_COLLECTIONS only swaps the standard
    // PersistentSet (HashSet-backed) for uninitialized collections; using LinkedHashSet would keep the
    // persistent wrapper around and fail with LazyInitializationException once the Hibernate session
    // closes (open-in-view is disabled). Insertion order is not meaningful here anyway.
    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties(value = "result", allowSetters = true)
    private Set<Feedback> feedbacks = new HashSet<>();

    /**
     * Automatic test-case feedback of programming exercises, split out of {@link #feedbacks} into a compact
     * table (one row per executed test; credits and visibility derived from the test case). Only populated
     * for programming-exercise results. See {@link TestCaseFeedback}.
     */
    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<TestCaseFeedback> testCaseFeedbacks = new HashSet<>();

    /**
     * Static-code-analysis feedback of programming exercises, split out of {@link #feedbacks} into a
     * structured table. Only populated for programming-exercise results. See {@link ScaFeedback}.
     */
    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<ScaFeedback> scaFeedbacks = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn()
    private User assessor;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type")
    private AssessmentType assessmentType;

    /**
     * Which correction round this result belongs to: 0 for the first correction, 1 for the second, and so on. Null for
     * automatic and Athena results, which are not correction rounds.
     * <p>
     * This used to be the position of the result inside its submission's result list, which is why that list carried an
     * order column. Keeping the round here means a result can be written without loading and re-saving the whole
     * submission just so Hibernate can renumber the list.
     */
    @Column(name = "correction_round")
    private Integer correctionRound;

    @Column(name = "has_complaint")
    private Boolean hasComplaint;

    @Column(name = "example_result")
    private Boolean exampleResult;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    // OneToMany is required, otherwise the lazy loading does not work
    // it will be ensured programmatically that only ever one note exists for every result object
    @JoinColumn(name = "result_id", nullable = false)
    private final List<AssessmentNote> assessmentNote = new ArrayList<>();

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

    @Column(name = "exercise_id", nullable = false)
    private long exerciseId;

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

    public Result exerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
        return this;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
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
     * @param score  new score
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
     * @param course      the course that specifies the accuracy
     */
    public void setScore(double totalPoints, double maxPoints, Course course) {
        if (maxPoints <= 0.0) {
            setScore(0.0, course);
            return;
        }

        setScore(totalPoints / maxPoints * 100, course);
    }

    /**
     * Checks whether the result is rated.
     *
     * @return true if the result is rated. If rated is null, it returns false.
     */
    public boolean isRated() {
        return Boolean.TRUE.equals(this.rated);
    }

    public Result rated(boolean rated) {
        this.rated = rated;
        return this;
    }

    public void setRated(boolean rated) {
        this.rated = rated;
    }

    private void setRatedIfNotAfterDueDate(@NonNull Participation participation, @NonNull ZonedDateTime submissionDate) {
        var optionalDueDate = ExerciseDateService.getDueDate(participation);
        if (optionalDueDate.isEmpty()) {
            this.rated = true;
            return;
        }
        var dueDate = optionalDueDate.get();
        if (getSubmission().getParticipation().getExercise() instanceof ProgrammingExercise) {
            dueDate = dueDate.plusSeconds(PROGRAMMING_GRACE_PERIOD_SECONDS);
        }
        this.rated = !submissionDate.isAfter(dueDate);
    }

    /**
     * A result is rated if:
     * - the submission date is before the due date OR
     * - no due date is set OR
     * - the submission type is INSTRUCTOR / TEST
     */
    public void setRatedIfNotAfterDueDate() {
        if (submission.getType() == SubmissionType.INSTRUCTOR || submission.getType() == SubmissionType.TEST) {
            this.rated = true;
        }
        else if (submission.getParticipation().isPracticeMode()) {
            this.rated = false;
        }
        else {
            setRatedIfNotAfterDueDate(submission.getParticipation(), submission.getSubmissionDate());
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

    /**
     * Returns the live, mutable set of feedbacks attached to this result.
     *
     * <p>
     * Returned as a {@link Set} on purpose: feedback ordering is not semantically meaningful for the
     * domain. Callers that need a specific presentation order (by credits, FeedbackType, reference, ...)
     * should sort explicitly with a custom comparator.
     *
     * @return the live {@link Set} of feedbacks; mutations are persisted by the Hibernate session
     */
    public Set<Feedback> getFeedbacks() {
        return feedbacks;
    }

    public Result feedbacks(Collection<Feedback> feedbacks) {
        setFeedbacks(feedbacks);
        return this;
    }

    /**
     * Adds the given feedback to this result, wiring the inverse side first so the FK is persisted.
     *
     * @param feedback the feedback to attach to this result
     * @return this result, for chaining
     */
    public Result addFeedback(Feedback feedback) {
        if (feedback == null) {
            return this;
        }
        feedback.setResult(this);
        this.feedbacks.add(feedback);
        return this;
    }

    public void addFeedbacks(Collection<Feedback> feedbacks) {
        if (feedbacks == null) {
            return;
        }
        feedbacks.forEach(this::addFeedback);
    }

    public void removeFeedback(Feedback feedback) {
        this.feedbacks.remove(feedback);
        feedback.setResult(null);
    }

    /**
     * Replaces the feedback collection with the given one. Each feedback's owning side is wired to
     * {@code this} so Hibernate persists the FK; orphaned feedback (those previously in the set but not
     * in the new collection) are dropped via {@code orphanRemoval = true}.
     * <p>
     * Replaces the field reference rather than calling {@code clear()} on the existing collection: when
     * the entity is detached and {@code feedbacks} is an uninitialized {@code PersistentSet}, calling
     * {@code clear()} would force initialization and throw {@code LazyInitializationException}.
     * Hibernate's snapshot-based dirty detection still computes orphans correctly when the reference is
     * swapped on an attached entity.
     *
     * @param feedbacks the new feedback collection (may be {@code null} or empty to clear)
     */
    public void setFeedbacks(Collection<Feedback> feedbacks) {
        Set<Feedback> newSet = new HashSet<>();
        if (feedbacks != null) {
            for (Feedback feedback : feedbacks) {
                if (feedback == null) {
                    // Tolerate null entries that may surface from legacy callers / tests; just skip them.
                    continue;
                }
                feedback.setResult(this);
                newSet.add(feedback);
            }
        }
        this.feedbacks = newSet;
    }

    /**
     * Returns the live, mutable set of test-case feedback rows attached to this result. Only meaningful for
     * programming-exercise results; empty otherwise.
     *
     * @return the live {@link Set} of test-case feedback; mutations are persisted by the Hibernate session
     */
    @JsonIgnore
    public Set<TestCaseFeedback> getTestCaseFeedbacks() {
        return testCaseFeedbacks;
    }

    /**
     * Replaces the test-case feedback collection, wiring the owning side of each row. Orphaned rows are
     * deleted via {@code orphanRemoval}. Replaces the field reference for the same lazy-initialization
     * reason as {@link #setFeedbacks(Collection)}.
     *
     * @param testCaseFeedbacks the new collection (may be {@code null} or empty to clear)
     */
    public void setTestCaseFeedbacks(Collection<TestCaseFeedback> testCaseFeedbacks) {
        Set<TestCaseFeedback> newSet = new HashSet<>();
        if (testCaseFeedbacks != null) {
            for (TestCaseFeedback feedback : testCaseFeedbacks) {
                if (feedback == null) {
                    continue;
                }
                feedback.setResult(this);
                newSet.add(feedback);
            }
        }
        this.testCaseFeedbacks = newSet;
    }

    /**
     * Adds the given test-case feedback to this result, wiring the owning side. Requires the target
     * collection to be initialized — use {@link #setTestCaseFeedbacks(Collection)} on detached results with
     * uninitialized collections.
     *
     * @param feedback the test-case feedback to attach
     */
    public void addTestCaseFeedback(TestCaseFeedback feedback) {
        if (feedback == null) {
            return;
        }
        feedback.setResult(this);
        this.testCaseFeedbacks.add(feedback);
    }

    /**
     * Returns the live, mutable set of static-code-analysis feedback rows attached to this result. Only
     * meaningful for programming-exercise results; empty otherwise.
     *
     * @return the live {@link Set} of SCA feedback; mutations are persisted by the Hibernate session
     */
    @JsonIgnore
    public Set<ScaFeedback> getScaFeedbacks() {
        return scaFeedbacks;
    }

    /**
     * Replaces the SCA feedback collection, wiring the owning side of each row. See
     * {@link #setTestCaseFeedbacks(Collection)}.
     *
     * @param scaFeedbacks the new collection (may be {@code null} or empty to clear)
     */
    public void setScaFeedbacks(Collection<ScaFeedback> scaFeedbacks) {
        Set<ScaFeedback> newSet = new HashSet<>();
        if (scaFeedbacks != null) {
            for (ScaFeedback feedback : scaFeedbacks) {
                if (feedback == null) {
                    continue;
                }
                feedback.setResult(this);
                newSet.add(feedback);
            }
        }
        this.scaFeedbacks = newSet;
    }

    /**
     * Adds the given SCA feedback to this result, wiring the owning side. Requires the target collection to
     * be initialized — use {@link #setScaFeedbacks(Collection)} on detached results with uninitialized
     * collections.
     *
     * @param feedback the SCA feedback to attach
     */
    public void addScaFeedback(ScaFeedback feedback) {
        if (feedback == null) {
            return;
        }
        feedback.setResult(this);
        this.scaFeedbacks.add(feedback);
    }

    /**
     * Assigns the given feedback list to the result. It first sets the positive flag and the feedback type of every feedback element, clears the existing list of feedback and
     * assigns the new feedback afterwards. IMPORTANT: This method should not be used for Quiz and Programming exercises with completely automatic assessments!
     *
     * @param feedbacks            the new feedback list
     * @param skipAutomaticResults if true automatic results won't be updated
     */
    public void updateAllFeedbackItems(List<Feedback> feedbacks, boolean skipAutomaticResults) {
        if (feedbacks == null) {
            return;
        }

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
     * Sets the feedback type of a new feedback element. The type is set to MANUAL if it was not set before. It is set to AUTOMATIC_ADAPTED if it was created
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
        return this.feedbacks.stream().filter(Objects::nonNull)
                .filter(existingFeedback -> existingFeedback.getReference() != null && existingFeedback.getReference().equals(feedback.getReference()))
                .anyMatch(sameFeedback -> !sameFeedback.getCredits().equals(feedback.getCredits()) || feedbackTextHasChanged(sameFeedback.getText(), feedback.getText()));
    }

    /**
     * Compares the given feedback texts (existingText and newText) and checks if the text has changed.
     */
    private boolean feedbackTextHasChanged(String existingText, String newText) {
        if (StringUtils.isEmpty(existingText) && StringUtils.isEmpty(newText)) {
            return false;
        }
        return !Objects.equals(existingText, newText);
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

    @Nullable
    public Integer getCorrectionRound() {
        return correctionRound;
    }

    public void setCorrectionRound(@Nullable Integer correctionRound) {
        this.correctionRound = correctionRound;
    }

    public Result assessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
        return this;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    /**
     * Determines the assessment type based on the feedback types. Sets the type to SEMI_AUTOMATIC if any feedback is automatic or automatically adapted.
     * Automatic test-case and SCA feedback live in their own collections, so their (initialized) presence also makes the result SEMI_AUTOMATIC.
     */
    public void determineAssessmentType() {
        setAssessmentType(AssessmentType.MANUAL);
        boolean hasAutomaticTypedFeedback = (Hibernate.isInitialized(testCaseFeedbacks) && !testCaseFeedbacks.isEmpty())
                || (Hibernate.isInitialized(scaFeedbacks) && !scaFeedbacks.isEmpty());
        if (hasAutomaticTypedFeedback || feedbacks.stream().filter(Objects::nonNull)
                .anyMatch(feedback -> feedback.getType() == FeedbackType.AUTOMATIC || feedback.getType() == FeedbackType.AUTOMATIC_ADAPTED)) {
            setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        }
    }

    public Boolean hasComplaint() {
        return hasComplaint;
    }

    /**
     * `hasComplaint` could be null in the database
     *
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

    /**
     * Checks the initialization status of the assessment note before returning. Only a single element is returned instead of the list,
     * because it is modelled that way on the client-side. Jackson therefore needs a single object for the (de-)serialization.
     *
     * @return Null, if the field is uninitialized or the encapsulating arraylist is empty, or else, the assessment note.
     */
    public AssessmentNote getAssessmentNote() {
        if (!Hibernate.isInitialized(assessmentNote) || assessmentNote.isEmpty()) {
            return null;
        }
        else {
            return assessmentNote.getFirst();
        }
    }

    /**
     * Clears the list before adding a new assessment note. This ensures that it contains at most one element.
     * When setting null, the list is just cleared, without adding anything afterward.
     *
     * @param assessmentNote The assessment note that is added to the list as its new sole element.
     */
    public void setAssessmentNote(AssessmentNote assessmentNote) {
        this.assessmentNote.clear();
        if (assessmentNote != null) {
            this.assessmentNote.add(assessmentNote);
        }
    }

    /**
     * Updates the attributes "score" and "successful" by evaluating its submission.
     * <b>Important</b>: the quizSubmission has to be loaded with eager submitted answers, otherwise this method will not work correctly
     *
     * @param quizExercise the quiz exercise for which the submission should be evaluated, must contain access to the course to calculate the score correctly
     */
    public void evaluateQuizSubmission(@NonNull QuizExercise quizExercise) {
        if (submission instanceof QuizSubmission quizSubmission) {
            // update score
            setScore(quizExercise.getScoreForSubmission(quizSubmission), quizExercise.getCourseViaExerciseGroupOrCourseMember());
        }
    }

    /**
     * Removes the assessor and the internal assessment note from the result, can be invoked to make sure that sensitive
     * information is not sent to the client. E.g. students should not see information about their assessor.
     * <p>
     * Does not filter feedbacks.
     */
    public void filterSensitiveInformation() {
        setAssessor(null);
        if (Hibernate.isInitialized(assessmentNote)) {
            setAssessmentNote(null);
        }
    }

    /**
     * Removes all feedback details that should not be passed to the student.
     *
     * @param removeHiddenFeedback true if feedbacks marked with visibility 'after due date' should also be removed.
     */
    public void filterSensitiveFeedbacks(boolean removeHiddenFeedback) {
        filterSensitiveFeedbacks(removeHiddenFeedback, submission.getParticipation().getExercise());
    }

    /**
     * Removes all feedback details that should not be passed to the student.
     *
     * @param removeHiddenFeedback true if feedbacks marked with visibility 'after due date' should also be removed.
     * @param exercise             the exercise related to this result. Used to determine if test case names should be removed.
     */
    public void filterSensitiveFeedbacks(boolean removeHiddenFeedback, Exercise exercise) {
        var filteredFeedback = createFilteredFeedbacks(removeHiddenFeedback, exercise);
        setFeedbacks(filteredFeedback);
        filterSensitiveTypedFeedbacks(removeHiddenFeedback, exercise);

        if (exercise instanceof ProgrammingExercise) {
            updateTestCaseCount();
        }
    }

    /**
     * Removes test-case feedback that should not be passed to the student from the (initialized) typed
     * collections: visibility is derived from the test case. Also strips test names when the exercise
     * hides them. Like {@link #filterSensitiveFeedbacks(boolean, Exercise)}, this must only be called on
     * detached entities right before serialization — on an attached entity {@code orphanRemoval} would
     * delete the filtered rows.
     *
     * @param removeHiddenFeedback true if feedback with visibility 'after due date' should also be removed
     * @param exercise             used to check if students can see the test case names
     */
    private void filterSensitiveTypedFeedbacks(boolean removeHiddenFeedback, Exercise exercise) {
        if (Hibernate.isInitialized(testCaseFeedbacks)) {
            var filtered = testCaseFeedbacks.stream().filter(feedback -> !feedback.isInvisible()).filter(feedback -> !removeHiddenFeedback || !feedback.isAfterDueDate())
                    .collect(Collectors.toCollection(HashSet::new));
            this.testCaseFeedbacks = filtered;

            if (exercise instanceof ProgrammingExercise programmingExercise && !Boolean.TRUE.equals(programmingExercise.getShowTestNamesToStudents())) {
                filtered.stream().filter(feedback -> feedback.getTestCase() != null).forEach(feedback -> feedback.getTestCase().setTestName(null));
            }
        }
        // SCA feedback has no visibility concept (it is always visible together with the result)
    }

    /**
     * Updates the testCaseCount and passedTestCaseCount attributes after filtering the feedback.
     * Test-case feedback lives in {@link #testCaseFeedbacks}; the legacy branch over {@link #feedbacks}
     * only matters for unsaved results whose typed collections were not populated (e.g. in tests).
     */
    private void updateTestCaseCount() {
        if (Hibernate.isInitialized(testCaseFeedbacks) && !testCaseFeedbacks.isEmpty()) {
            setTestCaseCount(testCaseFeedbacks.size());
            setPassedTestCaseCount((int) testCaseFeedbacks.stream().filter(feedback -> Boolean.TRUE.equals(feedback.isPositive())).count());
            return;
        }
        var testCaseFeedback = feedbacks.stream().filter(Objects::nonNull).filter(Feedback::isTestFeedback).toList();

        // TODO: this is not good code!
        setTestCaseCount(testCaseFeedback.size());
        setPassedTestCaseCount((int) testCaseFeedback.stream().filter(feedback -> Boolean.TRUE.equals(feedback.isPositive())).count());
    }

    /**
     * Returns a new list that only contains feedback that should be passed to the student.
     * Does not change the feedbacks attribute of this entity.
     * Also removes the test names from all feedback if it should not be shown to the student.
     *
     * @see ResultDTO
     *
     * @param removeHiddenFeedback true if feedbacks marked with visibility 'after due date' should also be removed.
     * @param exercise             used to check if students can see the test case names
     * @return the new filtered list
     */
    public List<Feedback> createFilteredFeedbacks(boolean removeHiddenFeedback, Exercise exercise) {
        var filteredFeedback = feedbacks.stream().filter(Objects::nonNull).filter(feedback -> !feedback.isInvisible())
                .filter(feedback -> !removeHiddenFeedback || !feedback.isAfterDueDate()).collect(Collectors.toCollection(ArrayList::new));

        if (exercise instanceof ProgrammingExercise programmingExercise && !Boolean.TRUE.equals(programmingExercise.getShowTestNamesToStudents())) {
            filteredFeedback.stream().filter(Feedback::isTestFeedback).forEach(feedback -> {
                if (feedback.getTestCase() != null) {
                    feedback.getTestCase().setTestName(null);
                }
            });

        }
        return filteredFeedback;
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

    /**
     * Checks whether the result is an automatic Athena result: AUTOMATIC_ATHENA
     *
     * @return true if the result is an automatic AI Athena result
     */
    @JsonIgnore
    public boolean isAthenaBased() {
        return AssessmentType.AUTOMATIC_ATHENA == assessmentType;
    }

    @Override
    public String toString() {
        return "Result{" + "id" + getId() + ", completionDate=" + completionDate + ", successful=" + successful + ", score=" + score + ", rated=" + rated + ", assessmentType="
                + assessmentType + ", hasComplaint=" + hasComplaint + ", testCaseCount=" + testCaseCount + ", passedTestCaseCount=" + passedTestCaseCount + ", codeIssueCount="
                + codeIssueCount + '}';
    }

    /**
     * Calculates the total score for programming exercises. Do not use it for other exercise types.
     * <p>
     * Test-case feedback does not store credits — they are derived from the test-case configuration.
     * Callers therefore pass the derived points per test case (see
     * {@code ProgrammingExerciseGradingService#calculateTestCasePoints}).
     *
     * @param pointsByTestCaseId derived points per test-case id for passed tests
     * @return calculated totalScore
     */
    public Double calculateTotalPointsForProgrammingExercises(Map<Long, Double> pointsByTestCaseId) {
        if (!Hibernate.isInitialized(testCaseFeedbacks) || !Hibernate.isInitialized(scaFeedbacks)) {
            // The automatic points come from these collections, so scoring without them would not fail - it would
            // quietly return a score that is missing every automatic test. Say so instead of relying on whether a
            // session happens to be open (none of the callers runs in one).
            throw new IllegalStateException(
                    "The typed automatic feedback of result " + getId() + " has to be loaded before its score is calculated, see ProgrammingFeedbackSynthesizerService");
        }
        double totalPoints = 0.0;
        double scoreAutomaticTests = 0.0;
        ProgrammingExercise programmingExercise = (ProgrammingExercise) submission.getParticipation().getExercise();
        Set<Feedback> feedbacks = getFeedbacks();
        var gradingInstructions = new HashMap<Long, Integer>(); // { instructionId: noOfEncounters }

        for (Feedback feedback : feedbacks) {
            if (feedback.isSynthesizedView()) {
                // A view of one of the typed rows summed below. Counting it here as well would award the automatic
                // points twice for every result that was passed through the synthesizer before being scored.
                continue;
            }
            if (feedback.getGradingInstruction() != null) {
                totalPoints = feedback.computeTotalScore(totalPoints, gradingInstructions);
            }
            else {
                // In case no structured grading instruction was applied on the assessment model we just sum the feedback credit (manual, unreferenced, adapted, and legacy rows).
                totalPoints += Objects.requireNonNullElse(feedback.getCredits(), 0.0);
            }
        }

        // Automatic test feedback: derived points for passed tests (capped below)
        for (TestCaseFeedback testCaseFeedback : testCaseFeedbacks) {
            if (Boolean.TRUE.equals(testCaseFeedback.isPositive()) && testCaseFeedback.getTestCase() != null) {
                scoreAutomaticTests += pointsByTestCaseId.getOrDefault(testCaseFeedback.getTestCase().getId(), 0.0);
            }
        }
        // Static code analysis feedback: negative credits from the graded penalty
        for (ScaFeedback scaFeedback : scaFeedbacks) {
            totalPoints += scaFeedback.getCredits();
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
     * Calculates and sets the score for programming exercises.
     *
     * @param exercise           the exercise
     * @param pointsByTestCaseId derived points per test-case id for passed tests (see
     *                               {@code ProgrammingExerciseGradingService#calculateTestCasePoints})
     */
    public void calculateScoreForProgrammingExercise(ProgrammingExercise exercise, Map<Long, Double> pointsByTestCaseId) {
        double totalPoints = calculateTotalPointsForProgrammingExercises(pointsByTestCaseId);
        setScore(totalPoints, exercise.getMaxPoints(), exercise.getCourseViaExerciseGroupOrCourseMember());
    }

    /**
     * Copies the relevant counters for programming exercises i.e. amount of (passed) test cases and code issues into this result
     *
     * @param originalResult the source for the values
     */
    public void copyProgrammingExerciseCounters(Result originalResult) {
        setTestCaseCount(originalResult.getTestCaseCount());
        setPassedTestCaseCount(originalResult.getPassedTestCaseCount());
        setCodeIssueCount(originalResult.getCodeIssueCount());
    }

    /**
     * Checks if this result has been assessed.
     *
     * @return true if there is a completion date, false otherwise
     */
    @JsonIgnore
    public boolean isAssessmentComplete() {
        return completionDate != null;
    }

    @Override
    public int compareTo(Result other) {
        if (getCompletionDate() == null || other.getCompletionDate() == null || Objects.equals(getCompletionDate(), other.getCompletionDate())) {
            // this case should not happen, but in the rare case we can compare the ids (in tests, the submission dates might be identical as ms are not stored in the database)
            // newer ids are typically later
            return getId().compareTo(other.getId());
        }
        return getCompletionDate().compareTo(other.getCompletionDate());
    }
}
