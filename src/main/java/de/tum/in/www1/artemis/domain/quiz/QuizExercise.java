package de.tum.in.www1.artemis.domain.quiz;

import static de.tum.in.www1.artemis.domain.enumeration.ExerciseType.QUIZ;

import java.time.ZonedDateTime;
import java.util.*;

import javax.annotation.Nullable;
import javax.persistence.*;
import javax.validation.constraints.NotNull;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * A QuizExercise contains multiple quiz quizQuestions, which can be either multiple choice, drag and drop or short answer. Artemis supports live quizzes with a start and end time which are
 * rated. Within this time, students can participate in the quiz and select their answers to the given quizQuestions. After the end time, the quiz is automatically evaluated
 * Instructors can choose to open the quiz for practice so that students can participate arbitrarily often with an unrated result
 */
@Entity
@DiscriminatorValue(value = "Q")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizExercise extends Exercise {

    @Column(name = "randomize_question_order")
    @JsonView(QuizView.Before.class)
    private Boolean randomizeQuestionOrder;

    // not used at the moment
    @Column(name = "allowed_number_of_attempts")
    @JsonView(QuizView.Before.class)
    private Integer allowedNumberOfAttempts;

    @Transient
    @JsonView(QuizView.Before.class)
    private transient Integer remainingNumberOfAttempts;

    @Column(name = "is_open_for_practice")
    @JsonView(QuizView.Before.class)
    private Boolean isOpenForPractice;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_mode")
    @JsonView(QuizView.Before.class)
    private QuizMode quizMode;

    /**
     * The duration of the quiz exercise in seconds
     */
    @Column(name = "duration")
    @JsonView(QuizView.Before.class)
    private Integer duration;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(unique = true)
    private QuizPointStatistic quizPointStatistic;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "exercise_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.During.class)
    private List<QuizQuestion> quizQuestions = new ArrayList<>();

    @OneToMany(mappedBy = "quizExercise", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private Set<QuizBatch> quizBatches = new HashSet<>();

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

    public QuizExercise duration(Integer duration) {
        this.duration = duration;
        return this;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
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

    @JsonView(QuizView.Before.class)
    public String getType() {
        return "quiz";
    }

    /**
     * Check if the quiz has started, that means quiz batches could potentially start
     *
     * @return true if quiz has started, false otherwise
     */
    @JsonView(QuizView.Before.class)
    public Boolean isQuizStarted() {
        return isVisibleToStudents();
    }

    /**
     * Check if the quiz has ended
     *
     * @return true if quiz has ended, false otherwise
     */
    @JsonView(QuizView.Before.class)
    public Boolean isQuizEnded() {
        return getDueDate() != null && ZonedDateTime.now().isAfter(getDueDate());
    }

    /**
     * Check if the quiz should be filtered for students (because it hasn't ended yet)
     *
     * @return true if quiz should be filtered, false otherwise
     */
    @JsonIgnore
    public Boolean shouldFilterForStudents() {
        return !isQuizEnded();
    }

    /**
     * Check if the quiz is valid. This means, the quiz needs a title, a valid duration, at least one question, and all quizQuestions must be valid
     *
     * @return true if the quiz is valid, otherwise false
     */
    @JsonIgnore
    public Boolean isValid() {
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

    public List<QuizQuestion> getQuizQuestions() {
        return quizQuestions;
    }

    public void addQuestions(QuizQuestion quizQuestion) {
        this.quizQuestions.add(quizQuestion);
        quizQuestion.setExercise(this);
    }

    public void setQuizQuestions(List<QuizQuestion> quizQuestions) {
        this.quizQuestions = quizQuestions;
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
        double score = getScoreInPointsForSubmission(quizSubmission);
        double maxPoints = getOverallQuizPoints();
        // map the resulting score to the 0 to 100 scale
        return 100.0 * score / maxPoints;
    }

    /**
     * Get the score for this submission as the number of points
     *
     * @param quizSubmission the submission that should be evaluated
     * @return the resulting score
     */
    public Double getScoreInPointsForSubmission(QuizSubmission quizSubmission) {
        double score = 0.0;
        // iterate through all quizQuestions of this quiz
        for (QuizQuestion quizQuestion : getQuizQuestions()) {
            // search for submitted answer for this quizQuestion
            SubmittedAnswer submittedAnswer = quizSubmission.getSubmittedAnswerForQuestion(quizQuestion);
            if (submittedAnswer != null) {
                score += quizQuestion.scoreForAnswer(submittedAnswer);
            }
        }
        return score;
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
    public StudentParticipation findRelevantParticipation(List<StudentParticipation> participations) {
        for (StudentParticipation participation : participations) {
            if (participation.getExercise() != null && participation.getExercise().equals(this)) {
                // in quiz exercises we don't care about the InitializationState
                // => return the first participation we find
                return participation;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public Submission findLatestSubmissionWithRatedResultWithCompletionDate(Participation participation, Boolean ignoreAssessmentDueDate) {
        // The shouldFilterForStudents() method uses the exercise release/due dates, not the ones of the exam, therefor we can only use them if this exercise is not part of an exam
        // In exams, all results should be seen as relevant as they will only be created once the exam is over
        if (shouldFilterForStudents() && !isExamExercise()) {
            // results are never relevant before quiz has ended => return null
            return null;
        }
        else {
            // only rated results are considered relevant
            Submission latestSubmission = null;
            if (participation.getSubmissions() == null || participation.getSubmissions().isEmpty()) {
                return null;
            }
            // we get the results over the submissions
            for (var submission : participation.getSubmissions()) {
                var result = submission.getLatestResult();
                if (result == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(result.isRated()) && result.getCompletionDate() != null) {
                    // take the first found result that fulfills the above requirements
                    if (latestSubmission == null) {
                        latestSubmission = submission;
                    }
                    // take newer results and thus disregard older ones
                    // this should actually not be the case for quiz exercises, because they only should have one rated result
                    else if (latestSubmission.getLatestResult().getCompletionDate().isBefore(result.getCompletionDate())) {
                        latestSubmission = submission;
                    }
                }
            }
            return latestSubmission;
        }
    }

    @Override
    public Set<Result> findResultsFilteredForStudents(Participation participation) {
        if (shouldFilterForStudents()) {
            // results are never relevant before quiz has ended => return null
            return null;
        }
        else {
            return participation.getResults();
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
     * Recreate missing pointers from children to parents that were removed by @JSONIgnore
     */
    public void reconnectJSONIgnoreAttributes() {
        // iterate through quizQuestions to add missing pointer back to quizExercise
        // Note: This is necessary because of the @IgnoreJSON in question and answerOption
        // that prevents infinite recursive JSON serialization.
        for (QuizQuestion quizQuestion : getQuizQuestions()) {
            if (quizQuestion.getId() != null) {
                quizQuestion.setExercise(this);
                // reconnect QuestionStatistics
                if (quizQuestion.getQuizQuestionStatistic() != null) {
                    quizQuestion.getQuizQuestionStatistic().setQuizQuestion(quizQuestion);
                }
                // do the same for answerOptions (if quizQuestion is multiple choice)
                if (quizQuestion instanceof MultipleChoiceQuestion mcQuestion) {
                    MultipleChoiceQuestionStatistic mcStatistic = (MultipleChoiceQuestionStatistic) mcQuestion.getQuizQuestionStatistic();
                    // reconnect answerCounters
                    for (AnswerCounter answerCounter : mcStatistic.getAnswerCounters()) {
                        if (answerCounter.getId() != null) {
                            answerCounter.setMultipleChoiceQuestionStatistic(mcStatistic);
                        }
                    }
                    // reconnect answerOptions
                    for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
                        if (answerOption.getId() != null) {
                            answerOption.setQuestion(mcQuestion);
                        }
                    }
                }
                if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                    DragAndDropQuestionStatistic dragAndDropStatistic = (DragAndDropQuestionStatistic) dragAndDropQuestion.getQuizQuestionStatistic();
                    // reconnect dropLocations
                    for (DropLocation dropLocation : dragAndDropQuestion.getDropLocations()) {
                        if (dropLocation.getId() != null) {
                            dropLocation.setQuestion(dragAndDropQuestion);
                        }
                    }
                    // reconnect dragItems
                    for (DragItem dragItem : dragAndDropQuestion.getDragItems()) {
                        if (dragItem.getId() != null) {
                            dragItem.setQuestion(dragAndDropQuestion);
                        }
                    }
                    // reconnect correctMappings
                    for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
                        if (mapping.getId() != null) {
                            mapping.setQuestion(dragAndDropQuestion);
                        }
                    }
                    // reconnect dropLocationCounters
                    for (DropLocationCounter dropLocationCounter : dragAndDropStatistic.getDropLocationCounters()) {
                        if (dropLocationCounter.getId() != null) {
                            dropLocationCounter.setDragAndDropQuestionStatistic(dragAndDropStatistic);
                            dropLocationCounter.getDropLocation().setQuestion(dragAndDropQuestion);
                        }
                    }
                }
                if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                    ShortAnswerQuestionStatistic shortAnswerStatistic = (ShortAnswerQuestionStatistic) shortAnswerQuestion.getQuizQuestionStatistic();
                    // reconnect spots
                    for (ShortAnswerSpot spot : shortAnswerQuestion.getSpots()) {
                        if (spot.getId() != null) {
                            spot.setQuestion(shortAnswerQuestion);
                        }
                    }
                    // reconnect solutions
                    for (ShortAnswerSolution solution : shortAnswerQuestion.getSolutions()) {
                        if (solution.getId() != null) {
                            solution.setQuestion(shortAnswerQuestion);
                        }
                    }
                    // reconnect correctMappings
                    for (ShortAnswerMapping mapping : shortAnswerQuestion.getCorrectMappings()) {
                        if (mapping.getId() != null) {
                            mapping.setQuestion(shortAnswerQuestion);
                        }
                    }
                    // reconnect spotCounters
                    for (ShortAnswerSpotCounter shortAnswerSpotCounter : shortAnswerStatistic.getShortAnswerSpotCounters()) {
                        if (shortAnswerSpotCounter.getId() != null) {
                            shortAnswerSpotCounter.setShortAnswerQuestionStatistic(shortAnswerStatistic);
                            shortAnswerSpotCounter.getSpot().setQuestion(shortAnswerQuestion);
                        }
                    }
                }
            }
        }

        // reconnect pointCounters
        for (PointCounter pointCounter : getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getId() != null) {
                pointCounter.setQuizPointStatistic(getQuizPointStatistic());
            }
        }

        if (getQuizBatches() != null) {
            for (QuizBatch quizBatch : getQuizBatches()) {
                quizBatch.setQuizExercise(this);
            }
        }
    }

    /**
     * add Result to all Statistics of the given QuizExercise
     *
     * @param result            the result which will be added
     * @param quizSubmission    the quiz submission which corresponds to the result and includes the submitted answers (loaded eagerly)
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
     * @param result       the result which will be removed (NOTE: add the submission to the result previously (this would improve the performance)
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

    /**
     * get the view for students in the given quiz
     *
     * @param batch The batch that the student that the view is for is currently a part of
     * @return the view depending on the current state of the quiz
     */
    @JsonIgnore
    @NotNull
    public Class<?> viewForStudentsInQuizExercise(@Nullable QuizBatch batch) {
        if (isQuizEnded()) {
            return QuizView.After.class;
        }
        else if (batch != null && batch.isSubmissionAllowed()) {
            return QuizView.During.class;
        }
        else {
            return QuizView.Before.class;
        }
    }

    @JsonIgnore
    @Override
    public void validateDates() {
        super.validateDates();
        quizBatches.forEach(quizBatch -> {
            if (quizBatch.getStartTime().isBefore(getReleaseDate())) {
                throw new BadRequestAlertException("Start time must not be before release date!", getTitle(), "noValidDates");
            }
        });
    }

    @Override
    public String toString() {
        return "QuizExercise{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", randomizeQuestionOrder='" + isRandomizeQuestionOrder() + "'" + ", allowedNumberOfAttempts='"
                + getAllowedNumberOfAttempts() + "'" + ", isOpenForPractice='" + isIsOpenForPractice() + "'" + ", releaseDate='" + getReleaseDate() + "'" + ", duration='"
                + getDuration() + "'" + ", dueDate='" + getDueDate() + "'" + "}";
    }
}
