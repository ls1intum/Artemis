package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A QuizExercise contains multiple quiz quizQuestions, which can be either multiple choice or drag and drop. Artemis supports live quizzes with a start and end time which are
 * rated. Within this time, students can participate in the quiz and select their answers to the given quizQuestions. After the end time, the quiz is automatically evaluated
 * Instructors can choose to open the quiz for practice so that students can participate arbitrarily often with an unrated result
 */
@Entity
@DiscriminatorValue(value = "Q")
public class QuizExercise extends Exercise implements Serializable {

    public enum Status {
        INACTIVE, STARTED, FINISHED
    }

    private static final long serialVersionUID = 1L;

    @Column(name = "randomize_question_order")
    @JsonView(QuizView.Before.class)
    private Boolean randomizeQuestionOrder;

    // not used at the moment
    @Column(name = "allowed_number_of_attempts")
    @JsonView(QuizView.Before.class)
    private Integer allowedNumberOfAttempts;

    @Column(name = "is_visible_before_start")
    @JsonView(QuizView.Before.class)
    private Boolean isVisibleBeforeStart;

    @Column(name = "is_open_for_practice")
    @JsonView(QuizView.Before.class)
    private Boolean isOpenForPractice;

    @Column(name = "is_planned_to_start")
    @JsonView(QuizView.Before.class)
    private Boolean isPlannedToStart;

    /**
     * The duration of the quiz exercise in seconds
     */
    @Column(name = "duration")
    @JsonView(QuizView.Before.class)
    private Integer duration;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(unique = true)
    @JsonView(QuizView.After.class)
    private QuizPointStatistic quizPointStatistic;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "exercise_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.During.class)
    private List<QuizQuestion> quizQuestions = new ArrayList<>();

    public Boolean isRandomizeQuestionOrder() {
        return randomizeQuestionOrder;
    }

    public QuizExercise randomizeQuestionOrder(Boolean randomizeQuestionOrder) {
        this.randomizeQuestionOrder = randomizeQuestionOrder;
        return this;
    }

    public void setRandomizeQuestionOrder(Boolean randomizeQuestionOrder) {
        this.randomizeQuestionOrder = randomizeQuestionOrder;
    }

    public Integer getAllowedNumberOfAttempts() {
        return allowedNumberOfAttempts;
    }

    public QuizExercise allowedNumberOfAttempts(Integer allowedNumberOfAttempts) {
        this.allowedNumberOfAttempts = allowedNumberOfAttempts;
        return this;
    }

    public void setAllowedNumberOfAttempts(Integer allowedNumberOfAttempts) {
        this.allowedNumberOfAttempts = allowedNumberOfAttempts;
    }

    public Boolean isIsVisibleBeforeStart() {
        return isVisibleBeforeStart;
    }

    public QuizExercise isVisibleBeforeStart(Boolean isVisibleBeforeStart) {
        this.isVisibleBeforeStart = isVisibleBeforeStart;
        return this;
    }

    public void setIsVisibleBeforeStart(Boolean isVisibleBeforeStart) {
        this.isVisibleBeforeStart = isVisibleBeforeStart;
    }

    public Boolean isIsOpenForPractice() {
        return isOpenForPractice;
    }

    public QuizExercise isOpenForPractice(Boolean isOpenForPractice) {
        this.isOpenForPractice = isOpenForPractice;
        return this;
    }

    public void setIsOpenForPractice(Boolean isOpenForPractice) {
        this.isOpenForPractice = isOpenForPractice;
    }

    public Boolean isIsPlannedToStart() {
        return isPlannedToStart;
    }

    public QuizExercise isPlannedToStart(Boolean isPlannedToStart) {
        this.isPlannedToStart = isPlannedToStart;
        return this;
    }

    public void setIsPlannedToStart(Boolean isPlannedToStart) {
        this.isPlannedToStart = isPlannedToStart;
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

    public QuizExercise quizPointStatistic(QuizPointStatistic quizPointStatistic) {
        this.quizPointStatistic = quizPointStatistic;
        return this;
    }

    public void setQuizPointStatistic(QuizPointStatistic quizPointStatistic) {
        this.quizPointStatistic = quizPointStatistic;
    }

    @JsonView(QuizView.Before.class)
    public String getType() {
        return "quiz";
    }

    @Override
    @JsonView(QuizView.Before.class)
    public ZonedDateTime getDueDate() {
        return isPlannedToStart ? getReleaseDate().plusSeconds(getDuration()) : null;
    }

    /**
     * Get the remaining time in seconds
     *
     * @return null, if the quiz is not planned to start, the remaining time in seconds otherwise
     */
    @JsonView(QuizView.Before.class)
    public Long getRemainingTime() {
        return isStarted() ? ChronoUnit.SECONDS.between(ZonedDateTime.now(), getDueDate()) : null;
    }

    /**
     * Get the remaining time until the quiz starts in seconds
     *
     * @return null, if the quiz isn't planned to start, otherwise the time until the quiz starts in seconds (negative if the quiz has already started)
     */
    @JsonView(QuizView.Before.class)
    public Long getTimeUntilPlannedStart() {
        return isIsPlannedToStart() ? ChronoUnit.SECONDS.between(ZonedDateTime.now(), getReleaseDate()) : null;
    }

    /**
     * Check if the quiz has started
     * 
     * @return true if quiz has started, false otherwise
     */
    @JsonView(QuizView.Before.class)
    public Boolean isStarted() {
        return isIsPlannedToStart() && ZonedDateTime.now().isAfter(getReleaseDate());
    }

    /**
     * Check if submissions for this quiz are allowed at the moment
     * 
     * @return true if submissions are allowed, false otherwise
     */
    @JsonIgnore
    public Boolean isSubmissionAllowed() {
        return isStarted() && getRemainingTime() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS > 0;
    }

    /**
     * Check if the quiz has ended
     * 
     * @return true if quiz has ended, false otherwise
     */
    @JsonView(QuizView.Before.class)
    @Override
    public Boolean isEnded() {
        return isStarted() && getRemainingTime() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS <= 0;
    }

    /**
     * Check if the quiz should be filtered for students (because it hasn't ended yet)
     * 
     * @return true if quiz should be filtered, false otherwise
     */
    @JsonIgnore
    public Boolean shouldFilterForStudents() {
        return !isStarted() || isSubmissionAllowed();
    }

    /**
     * Check if the quiz is valid. This means, the quiz needs a title, a valid duration, at least one question, and all quizQuestions must be valid
     *
     * @return true if the quiz is valid, otherwise false
     */
    @JsonIgnore
    public Boolean isValid() {
        // check title
        if (getTitle() == null || getTitle().equals("")) {
            return false;
        }

        // check duration
        if (getDuration() == null || getDuration() < 0) {
            return false;
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

    /**
     * 1. replace the old QuizQuestion-List with the new one 2. recalculate the PointCounters in quizPointStatistic
     *
     * @param quizQuestions the List of QuizQuestion objects which will be set
     * @return this QuizExercise-object
     */
    public QuizExercise questions(List<QuizQuestion> quizQuestions) {
        this.quizQuestions = quizQuestions;
        // correct the associated quizPointStatistic implicitly
        recalculatePointCounters();
        return this;
    }

    /**
     * 1. add the new QuizQuestion object to the QuizQuestion-List 2. add backward relation in the quizQuestion-object 3. recalculate the PointCounters in quizPointStatistic
     *
     * @param quizQuestion the new QuizQuestion object which will be added
     * @return this QuizExercise-object
     */
    public QuizExercise addQuestions(QuizQuestion quizQuestion) {
        this.quizQuestions.add(quizQuestion);
        quizQuestion.setExercise(this);
        // correct the associated quizPointStatistic implicitly
        recalculatePointCounters();
        return this;
    }

    /**
     * 1. remove the given QuizQuestion object in the QuizQuestion-List 2. remove backward relation in the quizQuestion-object 3. recalculate the PointCounters in
     * quizPointStatistic
     *
     * @param quizQuestion the QuizQuestion object which should be removed
     * @return this QuizExercise-object
     */
    public QuizExercise removeQuestions(QuizQuestion quizQuestion) {
        this.quizQuestions.remove(quizQuestion);
        quizQuestion.setExercise(null);
        // correct the associated quizPointStatistic implicitly
        recalculatePointCounters();
        return this;
    }

    /**
     * 1. replace the old QuizQuestion-List with the new one 2. recalculate the PointCounters in quizPointStatistic
     *
     * @param quizQuestions the List of QuizQuestion objects which will be set
     */
    public void setQuizQuestions(List<QuizQuestion> quizQuestions) {
        this.quizQuestions = quizQuestions;
        if (quizQuestions != null) {
            recalculatePointCounters();
        }
    }

    @Override
    public Boolean isVisibleToStudents() {
        return isVisibleBeforeStart || (isPlannedToStart && getReleaseDate() != null && getReleaseDate().isBefore(ZonedDateTime.now()));
    }

    /**
     * filter this quiz exercise for students depending on the quiz's current state
     */
    public void applyAppropriateFilterForStudents() {
        if (!isStarted()) {
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
    public Long getScoreForSubmission(QuizSubmission quizSubmission) {
        double score = getScoreInPointsForSubmission(quizSubmission);
        int maxScore = getMaxTotalScore();
        // map the resulting score to the 0 to 100 scale
        return Math.round(100.0 * score / maxScore);
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
        if (shouldFilterForStudents()) {
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
                var result = submission.getResult();
                if (result == null) {
                    continue;
                }
                if (result.isRated() == Boolean.TRUE && result.getCompletionDate() != null) {
                    // take the first found result that fulfills the above requirements
                    if (latestSubmission == null) {
                        latestSubmission = submission;
                    }
                    // take newer results and thus disregard older ones
                    // this should actually not be the case for quiz exercises, because they only should have one rated result
                    else if (latestSubmission.getResult().getCompletionDate().isBefore(result.getCompletionDate())) {
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

    /**
     * undo all changes which are not allowed after the dueDate ( dueDate, releaseDate, question.points, adding Questions and Answers)
     *
     * @param originalQuizExercise the original QuizExercise object, which will be compared with this quizExercise
     */
    public void undoUnallowedChanges(QuizExercise originalQuizExercise) {

        // reset unchangeable attributes: ( dueDate, releaseDate, question.points)
        this.setDueDate(originalQuizExercise.getDueDate());
        this.setReleaseDate(originalQuizExercise.getReleaseDate());

        // remove added Questions, which are not allowed to be added
        Set<QuizQuestion> addedQuizQuestions = new HashSet<>();

        // check every question
        for (QuizQuestion quizQuestion : quizQuestions) {
            // check if the quizQuestion were already in the originalQuizExercise -> if not it's an added quizQuestion
            if (originalQuizExercise.getQuizQuestions().contains(quizQuestion)) {
                // find original unchanged quizQuestion
                QuizQuestion originalQuizQuestion = originalQuizExercise.findQuestionById(quizQuestion.getId());
                // reset score (not allowed to change)
                quizQuestion.setScore(originalQuizQuestion.getScore());
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
                        || (quizQuestion.isInvalid() && !originalQuizQuestion.isInvalid()) || !quizQuestion.getScoringType().equals(originalQuizQuestion.getScoringType());

                // check if the quizQuestion-changes make an update of the statistics and results necessary
                updateOfResultsAndStatisticsNecessary = updateOfResultsAndStatisticsNecessary || quizQuestion.isUpdateOfResultsAndStatisticsNecessary(originalQuizQuestion);
            }
        }
        // check if an question was deleted (not allowed added quistions are not relevant)
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
    public Integer getMaxTotalScore() {
        int maxScore = 0;
        // iterate through all quizQuestions of this quiz and add up the score
        if (quizQuestions != null && Hibernate.isInitialized(quizQuestions)) {
            for (QuizQuestion quizQuestion : getQuizQuestions()) {
                maxScore += quizQuestion.getScore();
            }
        }
        return maxScore;
    }

    @Override
    public Double getMaxScore() {
        // this is a temporary solution for legacy exercises where maxScore was not set
        Double score = super.getMaxScore();
        if (score != null) {
            return score;
        }
        else if (quizQuestions != null && Hibernate.isInitialized(quizQuestions)) {
            return getMaxTotalScore().doubleValue();
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuizExercise quizExercise = (QuizExercise) o;
        if (quizExercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), quizExercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "QuizExercise{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", randomizeQuestionOrder='" + isRandomizeQuestionOrder() + "'" + ", allowedNumberOfAttempts='"
                + getAllowedNumberOfAttempts() + "'" + ", isVisibleBeforeStart='" + isIsVisibleBeforeStart() + "'" + ", isOpenForPractice='" + isIsOpenForPractice() + "'"
                + ", isPlannedToStart='" + isIsPlannedToStart() + "'" + ", duration='" + getDuration() + "'" + "}";
    }

    /**
     * Constructor. 1. generate associated QuizPointStatistic implicitly
     */
    public QuizExercise() {
        // creates the associated quizPointStatistic implicitly
        quizPointStatistic = new QuizPointStatistic();
        quizPointStatistic.setQuiz(this);
    }

    /**
     * correct the associated quizPointStatistic implicitly 1. add new PointCounters for new Scores 2. delete old PointCounters if the score is no longer contained
     */
    private void recalculatePointCounters() {
        if (quizPointStatistic == null || !Hibernate.isInitialized(quizPointStatistic)) {
            return;
        }

        double quizScore = getMaxTotalScore();

        // add new PointCounter
        for (double i = 0.0; i <= quizScore; i++) {  // for variable ScoreSteps change: i++ into: i= i + scoreStep
            quizPointStatistic.addScore(i);
        }
        // delete old PointCounter
        Set<PointCounter> pointCounterToDelete = new HashSet<>();
        for (PointCounter pointCounter : quizPointStatistic.getPointCounters()) {
            if (pointCounter.getId() != null) {                                                                                        // for variable ScoreSteps add:
                if (pointCounter.getPoints() > quizScore || pointCounter.getPoints() < 0 || quizQuestions == null
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
                if (quizQuestion instanceof MultipleChoiceQuestion) {
                    MultipleChoiceQuestion mcQuestion = (MultipleChoiceQuestion) quizQuestion;
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
                if (quizQuestion instanceof DragAndDropQuestion) {
                    DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizQuestion;
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
                if (quizQuestion instanceof ShortAnswerQuestion) {
                    ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) quizQuestion;
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
        // reconnect quizPointStatistic
        getQuizPointStatistic().setQuiz(this);
        // reconnect pointCounters
        for (PointCounter pointCounter : getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getId() != null) {
                pointCounter.setQuizPointStatistic(getQuizPointStatistic());
            }
        }
    }

    /**
     * Determines the Status of a QuizExercise
     *
     * @param quiz the Quiz for which the status should be determined
     * @return the Status of the given Quiz
     */
    public static Status statusForQuiz(QuizExercise quiz) {
        if (!quiz.isPlannedToStart || quiz.getReleaseDate().isAfter(ZonedDateTime.now())) {
            return Status.INACTIVE;
        }
        else if (quiz.getDueDate().isBefore(ZonedDateTime.now())) {
            return Status.FINISHED;
        }
        else {
            return Status.STARTED;
        }
    }
}
