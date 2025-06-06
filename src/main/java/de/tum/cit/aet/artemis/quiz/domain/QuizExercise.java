package de.tum.cit.aet.artemis.quiz.domain;

import static de.tum.cit.aet.artemis.exercise.domain.ExerciseType.QUIZ;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Transient;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * A QuizExercise contains multiple quiz quizQuestions, which can be either multiple choice, drag and drop or short answer. Artemis supports live quizzes with a start and end time
 * which are
 * rated. Within this time, students can participate in the quiz and select their answers to the given quizQuestions. After the end time, the quiz is automatically evaluated
 * Instructors can choose to open the quiz for practice so that students can participate arbitrarily often with an unrated result
 */
@Entity
@DiscriminatorValue(value = "Q")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizExercise extends Exercise implements QuizConfiguration {

    @Column(name = "randomize_question_order")
    private Boolean randomizeQuestionOrder;

    // not used at the moment
    @Column(name = "allowed_number_of_attempts")
    private Integer allowedNumberOfAttempts;

    @Transient
    private transient Integer remainingNumberOfAttempts;

    @Column(name = "is_open_for_practice")
    private Boolean isOpenForPractice;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_mode", columnDefinition = "varchar(63) default 'SYNCHRONIZED'", nullable = false)
    private QuizMode quizMode = QuizMode.SYNCHRONIZED; // default value

    /**
     * The duration of the quiz exercise in seconds
     */
    @Column(name = "duration")
    private Integer duration;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(unique = true)
    private QuizPointStatistic quizPointStatistic;

    // TODO: test if we should use mappedBy here as well
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "exercise_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<QuizQuestion> quizQuestions = new ArrayList<>();

    @OneToMany(mappedBy = "quizExercise", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<QuizBatch> quizBatches = new HashSet<>();

    // used to distinguish the type when used in collections (e.g. SearchResultPageDTO --> resultsOnPage)
    @Override
    public String getType() {
        return "quiz";
    }

    public Boolean isRandomizeQuestionOrder() {
        return randomizeQuestionOrder;
    }

    public void setRandomizeQuestionOrder(Boolean randomizeQuestionOrder) {
        this.randomizeQuestionOrder = randomizeQuestionOrder;
    }

    public Integer getAllowedNumberOfAttempts() {
        return allowedNumberOfAttempts;
    }

    public void setAllowedNumberOfAttempts(Integer allowedNumberOfAttempts) {
        this.allowedNumberOfAttempts = allowedNumberOfAttempts;
    }

    @JsonProperty
    public Integer getRemainingNumberOfAttempts() {
        return remainingNumberOfAttempts;
    }

    public void setRemainingNumberOfAttempts(Integer remainingNumberOfAttempts) {
        this.remainingNumberOfAttempts = remainingNumberOfAttempts;
    }

    public Boolean isIsOpenForPractice() {
        return isOpenForPractice;
    }

    public void setIsOpenForPractice(Boolean isOpenForPractice) {
        this.isOpenForPractice = isOpenForPractice;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public QuizExercise duration(Integer duration) {
        this.duration = duration;
        return this;
    }

    public QuizPointStatistic getQuizPointStatistic() {
        return quizPointStatistic;
    }

    public void setQuizPointStatistic(QuizPointStatistic quizPointStatistic) {
        this.quizPointStatistic = quizPointStatistic;
    }

    public Set<QuizBatch> getQuizBatches() {
        return quizBatches;
    }

    public void setQuizBatches(Set<QuizBatch> quizBatches) {
        this.quizBatches = quizBatches;
    }

    public QuizMode getQuizMode() {
        return quizMode;
    }

    public void setQuizMode(QuizMode quizMode) {
        this.quizMode = quizMode;
    }

    /**
     * Check if the quiz has started, that means quiz batches could potentially start
     *
     * @return true if quiz has started, false otherwise
     */
    public boolean isQuizStarted() {
        return isVisibleToStudents();
    }

    /**
     * Check if the quiz has ended
     *
     * @return true if quiz has ended, false otherwise
     */
    public boolean isQuizEnded() {
        return getDueDate() != null && ZonedDateTime.now().isAfter(getDueDate());
    }

    /**
     * Check if the quiz should be filtered for students (because it hasn't ended yet)
     *
     * @return true if quiz should be filtered, false otherwise
     */
    @JsonIgnore
    public boolean shouldFilterForStudents() {
        return !isQuizEnded();
    }

    /**
     * Check if the quiz is valid. This means, the quiz needs a title, a valid duration, at least one question, and all quizQuestions must be valid
     *
     * @return true if the quiz is valid, otherwise false
     */
    @JsonIgnore
    public boolean isValid() {
        // check title
        if (getTitle() == null || getTitle().isEmpty()) {
            return false;
        }

        // check duration (only for course exercises)
        if (isCourseExercise()) {
            if (getDuration() == null || getDuration() < 0) {
                return false;
            }
        }

        // check quizQuestions
        if (getQuizQuestions() == null || getQuizQuestions().isEmpty()) {
            return false;
        }
        for (QuizQuestion quizQuestion : getQuizQuestions()) {
            if (!quizQuestion.isValid()) {
                return false;
            }
        }

        // passed all checks
        return true;
    }

    @Override
    public List<QuizQuestion> getQuizQuestions() {
        return quizQuestions;
    }

    public void setQuizQuestions(List<QuizQuestion> quizQuestions) {
        this.quizQuestions = quizQuestions;
    }

    public void addQuestions(QuizQuestion quizQuestion) {
        this.quizQuestions.add(quizQuestion);
        quizQuestion.setExercise(this);
    }

    /**
     * filter this quiz exercise for students depending on the current state of the batch that the student participates in
     *
     * @param batch The batch that the student that should be filtered for is currrently in
     */
    public void applyAppropriateFilterForStudents(@Nullable QuizBatch batch) {
        if (isQuizEnded()) {
            return; // no filtering required after the end of the quiz
        }
        if (batch == null || !batch.isSubmissionAllowed()) {
            filterSensitiveInformation();
        }
        else if (shouldFilterForStudents()) {
            filterForStudentsDuringQuiz();
        }
    }

    /**
     * set all sensitive information to null, so no info with respect to the solution gets leaked to students through json
     */
    @Override
    public void filterSensitiveInformation() {
        setQuizPointStatistic(null);
        setQuizQuestions(new ArrayList<>());
        super.filterSensitiveInformation();
    }

    /**
     * filter out information about correct answers, so no info with respect to the solution gets leaked to students through json
     */
    public void filterForStudentsDuringQuiz() {
        // filter out statistics
        setQuizPointStatistic(null);

        // filter out statistics, explanations, and any information about correct answers
        // from all quizQuestions (so students can't find them in the JSON while answering the quiz)
        for (QuizQuestion quizQuestion : this.getQuizQuestions()) {
            quizQuestion.filterForStudentsDuringQuiz();
        }
    }

    /**
     * filter out information about correct answers
     */
    public void filterForStatisticWebsocket() {

        // filter out explanations, and any information about correct answers
        // from all quizQuestions (so students can't find them in the JSON while answering the quiz)
        for (QuizQuestion quizQuestion : this.getQuizQuestions()) {
            quizQuestion.filterForStatisticWebsocket();
        }
    }

    /**
     * Get the score for this submission as a number from 0 to 100 (100 being the best possible result)
     *
     * @param quizSubmission the submission that should be evaluated
     * @return the resulting score
     */
    public Double getScoreForSubmission(QuizSubmission quizSubmission) {
        double score = quizSubmission.getScoreInPoints(getQuizQuestions());
        double maxPoints = getOverallQuizPoints();
        // map the resulting score to the 0 to 100 scale
        return 100.0 * score / maxPoints;
    }

    /**
     * Get question by ID
     *
     * @param questionId the ID of the question, which should be found
     * @return the question with the given ID, or null if the question is not contained in the quizExercise
     */
    public QuizQuestion findQuestionById(Long questionId) {

        if (questionId != null) {
            // iterate through all quizQuestions of this quiz
            for (QuizQuestion quizQuestion : quizQuestions) {
                // return quizQuestion if the IDs are equal
                if (quizQuestion.getId().equals(questionId)) {
                    return quizQuestion;
                }
            }
        }
        return null;
    }

    @Override
    public void filterResultsForStudents(Participation participation) {
        if (shouldFilterForStudents()) {
            // results are never relevant before quiz has ended => clear all results
            participation.getSubmissions().forEach(submission -> {
                List<Result> results = submission.getResults();
                if (results != null) {
                    results.clear();
                }
            });
        }
    }

    @Override
    public ExerciseType getExerciseType() {
        return QUIZ;
    }

    /**
     * undo all changes which are not allowed after the dueDate ( dueDate, releaseDate, 'question.points', adding Questions and Answers)
     *
     * @param originalQuizExercise the original QuizExercise object, which will be compared with this quizExercise
     */
    public void undoUnallowedChanges(QuizExercise originalQuizExercise) {

        // reset unchangeable attributes: ( dueDate, releaseDate, question.points)
        this.setDueDate(originalQuizExercise.getDueDate());
        this.setReleaseDate(originalQuizExercise.getReleaseDate());
        this.setStartDate(originalQuizExercise.getStartDate());

        // cannot update batches
        this.setQuizBatches(originalQuizExercise.getQuizBatches());

        // remove added Questions, which are not allowed to be added
        Set<QuizQuestion> addedQuizQuestions = new HashSet<>();

        // check every question
        for (QuizQuestion quizQuestion : quizQuestions) {
            // check if the quizQuestion were already in the originalQuizExercise -> if not it's an added quizQuestion
            if (originalQuizExercise.getQuizQuestions().contains(quizQuestion)) {
                // find original unchanged quizQuestion
                QuizQuestion originalQuizQuestion = originalQuizExercise.findQuestionById(quizQuestion.getId());
                // reset score (not allowed changing)
                quizQuestion.setPoints(originalQuizQuestion.getPoints());
                // correct invalid = null to invalid = false
                if (quizQuestion.isInvalid() == null) {
                    quizQuestion.setInvalid(false);
                }
                // reset invalid if the quizQuestion is already invalid
                quizQuestion.setInvalid(quizQuestion.isInvalid() || (originalQuizQuestion.isInvalid() != null && originalQuizQuestion.isInvalid()));

                // undo all not allowed changes in the answers of the QuizQuestion
                quizQuestion.undoUnallowedChanges(originalQuizQuestion);

            }
            else {
                // quizQuestion is added (not allowed), mark quizQuestion for remove
                addedQuizQuestions.add(quizQuestion);
            }
        }
        // remove all added quizQuestions
        quizQuestions.removeAll(addedQuizQuestions);
    }

    /**
     * check if an update of the Results and Statistics is necessary after the re-evaluation of this quiz
     *
     * @param originalQuizExercise the original QuizExercise object, which will be compared with this quizExercise
     * @return a boolean which is true if an update is necessary and false if not
     */
    public boolean checkIfRecalculationIsNecessary(QuizExercise originalQuizExercise) {

        boolean updateOfResultsAndStatisticsNecessary = false;

        // check every question
        for (QuizQuestion quizQuestion : quizQuestions) {
            // check if the quizQuestion were already in the originalQuizExercise
            if (originalQuizExercise.getQuizQuestions().contains(quizQuestion)) {
                // find original unchanged quizQuestion
                QuizQuestion originalQuizQuestion = originalQuizExercise.findQuestionById(quizQuestion.getId());

                // check if a quizQuestion is set invalid or if the scoringType has changed
                // if true an update of the Statistics and Results is necessary
                updateOfResultsAndStatisticsNecessary = updateOfResultsAndStatisticsNecessary || (quizQuestion.isInvalid() && originalQuizQuestion.isInvalid() == null)
                        || (quizQuestion.isInvalid() && !originalQuizQuestion.isInvalid()) || !Objects.equals(quizQuestion.getScoringType(), originalQuizQuestion.getScoringType());

                // check if the quizQuestion-changes make an update of the statistics and results necessary
                updateOfResultsAndStatisticsNecessary = updateOfResultsAndStatisticsNecessary || quizQuestion.isUpdateOfResultsAndStatisticsNecessary(originalQuizQuestion);
            }
        }
        // check if a question was deleted (not allowed added questions are not relevant)
        // if true an update of the Statistics and Results is necessary
        if (quizQuestions.size() != originalQuizExercise.getQuizQuestions().size()) {
            updateOfResultsAndStatisticsNecessary = true;
        }
        return updateOfResultsAndStatisticsNecessary;
    }

    /**
     * Get the maximum total score for this quiz
     *
     * @return the sum of all the quizQuestions' maximum scores
     */
    @JsonIgnore
    public Double getOverallQuizPoints() {
        double maxPoints = 0.0;
        // iterate through all quizQuestions of this quiz and add up the score
        if (quizQuestions != null && Hibernate.isInitialized(quizQuestions)) {
            for (QuizQuestion quizQuestion : getQuizQuestions()) {
                maxPoints += quizQuestion.getPoints();
            }
        }
        return maxPoints;
    }

    @Override
    public Double getMaxPoints() {
        // this is a temporary solution for legacy exercises where maxScore was not set
        Double score = super.getMaxPoints();
        if (score != null) {
            return score;
        }
        else if (quizQuestions != null && Hibernate.isInitialized(quizQuestions)) {
            return getOverallQuizPoints();
        }
        return null;
    }

    /**
     * correct the associated quizPointStatistic
     * 1. add new PointCounters for new Scores
     * 2. delete old PointCounters if the score is no longer contained
     */
    public void recalculatePointCounters() {
        if (quizPointStatistic == null || !Hibernate.isInitialized(quizPointStatistic)) {
            return;
        }

        double quizPoints = getOverallQuizPoints();

        // add new PointCounter
        for (double i = 0.0; i <= quizPoints; i++) {  // for variable ScoreSteps change: i++ into: i= i + scoreStep
            quizPointStatistic.addScore(i);
        }
        // delete old PointCounter
        Set<PointCounter> pointCounterToDelete = new HashSet<>();
        for (PointCounter pointCounter : quizPointStatistic.getPointCounters()) {
            if (pointCounter.getId() != null) {                                                                                        // for variable ScoreSteps add:
                if (pointCounter.getPoints() > quizPoints || pointCounter.getPoints() < 0 || quizQuestions == null
                        || quizQuestions.isEmpty()/* || (pointCounter.getPoints()% scoreStep) != 0 */) {
                    pointCounterToDelete.add(pointCounter);
                    pointCounter.setQuizPointStatistic(null);
                }
            }
        }
        quizPointStatistic.getPointCounters().removeAll(pointCounterToDelete);
    }

    /**
     * add Result to all Statistics of the given QuizExercise
     *
     * @param result         the result which will be added
     * @param quizSubmission the quiz submission which corresponds to the result and includes the submitted answers (loaded eagerly)
     */
    public void addResultToAllStatistics(Result result, QuizSubmission quizSubmission) {

        // update QuizPointStatistic with the result
        if (result != null) {
            getQuizPointStatistic().addResult(result.getScore(), result.isRated());
            for (QuizQuestion quizQuestion : getQuizQuestions()) {
                // update QuestionStatistics with the result
                if (quizQuestion.getQuizQuestionStatistic() != null && quizSubmission != null) {
                    quizQuestion.getQuizQuestionStatistic().addResult(quizSubmission.getSubmittedAnswerForQuestion(quizQuestion), result.isRated());
                }
            }
        }
    }

    /**
     * remove Result from all Statistics of the given QuizExercise
     *
     * @param result the result which will be removed (NOTE: add the submission to the result previously (this would improve the performance)
     */
    public void removeResultFromAllStatistics(Result result) {
        // update QuizPointStatistic with the result
        if (result != null) {
            // check if result contains a quizSubmission if true -> it's not necessary to fetch it from the database
            QuizSubmission quizSubmission = (QuizSubmission) result.getSubmission();
            getQuizPointStatistic().removeOldResult(result.getScore(), result.isRated());
            for (QuizQuestion quizQuestion : getQuizQuestions()) {
                // update QuestionStatistics with the result
                if (quizQuestion.getQuizQuestionStatistic() != null) {
                    quizQuestion.getQuizQuestionStatistic().removeOldResult(quizSubmission.getSubmittedAnswerForQuestion(quizQuestion), result.isRated());
                }
            }
        }
    }

    @JsonIgnore
    @Override
    public void validateDates() {
        super.validateDates();
        quizBatches.forEach(quizBatch -> {
            if (quizBatch.getStartTime() != null && getReleaseDate() != null && quizBatch.getStartTime().isBefore(getReleaseDate())) {
                throw new BadRequestAlertException("Start time must not be before release date!", getTitle(), "noValidDates");
            }
        });
    }

    @Override
    public void setQuestionParent(QuizQuestion quizQuestion) {
        quizQuestion.setExercise(this);
    }

    /**
     * Recreate missing pointers from children to parents that were removed by @JSONIgnore
     */
    @Override
    public void reconnectJSONIgnoreAttributes() {
        QuizConfiguration.super.reconnectJSONIgnoreAttributes();

        // reconnect pointCounters
        for (PointCounter pointCounter : getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getId() != null) {
                pointCounter.setQuizPointStatistic(getQuizPointStatistic());
            }
        }

        // reconnect quizBatches
        if (getQuizBatches() != null) {
            for (QuizBatch quizBatch : getQuizBatches()) {
                quizBatch.setQuizExercise(this);
            }
        }
    }

    @Override
    public String toString() {
        return "QuizExercise{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", randomizeQuestionOrder='" + isRandomizeQuestionOrder() + "'" + ", allowedNumberOfAttempts='"
                + getAllowedNumberOfAttempts() + "'" + ", isOpenForPractice='" + isIsOpenForPractice() + "'" + ", releaseDate='" + getReleaseDate() + "'" + ", duration='"
                + getDuration() + "'" + ", dueDate='" + getDueDate() + "'" + "}";
    }
}
