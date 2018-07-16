package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * A QuizExercise.
 */
@Entity
@DiscriminatorValue(value="Q")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class QuizExercise extends Exercise implements Serializable, SchedulableExercise {

    public enum Status {
        INACTIVE, STARTED, FINISHED
    }

    public static Status statusForQuiz(QuizExercise quiz) {
        if (!quiz.isPlannedToStart || quiz.getReleaseDate().isAfter(ZonedDateTime.now())) {
            return Status.INACTIVE;
        } else if (quiz.getDueDate().isBefore(ZonedDateTime.now())) {
            return Status.FINISHED;
        } else {
            return Status.STARTED;
        }
    }

    private static final long serialVersionUID = 1L;

    @Column(name = "description")
    @JsonView(QuizView.Before.class)
    private String description;

    @Column(name = "explanation")
    @JsonView(QuizView.After.class)
    private String explanation;

    @Column(name = "randomize_question_order")
    @JsonView(QuizView.Before.class)
    private Boolean randomizeQuestionOrder;

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

    @LazyToOne(LazyToOneOption.NO_PROXY)
    @OneToOne(cascade=CascadeType.ALL, orphanRemoval=true)
    @JoinColumn(unique = true)
    @JsonView(QuizView.After.class)
    private QuizPointStatistic quizPointStatistic;

    @OneToMany(cascade=CascadeType.ALL, orphanRemoval=true)
    @OrderColumn
    @JoinColumn(name="exercise_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.During.class)
    private List<Question> questions = new ArrayList<>();

    public String getDescription() {
        return description;
    }

    public QuizExercise description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExplanation() {
        return explanation;
    }

    public QuizExercise explanation(String explanation) {
        this.explanation = explanation;
        return this;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

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
     * @return true if quiz has started, false otherwise
     */
    @JsonView(QuizView.Before.class)
    public Boolean isStarted() {
        return isIsPlannedToStart() && ZonedDateTime.now().isAfter(getReleaseDate());
    }

    /**
     * Check if submissions for this quiz are allowed at the moment
     * @return true if submissions are allowed, false otherwise
     */
    @JsonIgnore
    public Boolean isSubmissionAllowed() {
        return isStarted() && getRemainingTime() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS > 0;
    }

    /**
     * Check if the quiz has ended
     * @return true if quiz has ended, false otherwise
     */
    @JsonView(QuizView.Before.class)
    public Boolean isEnded() {
        return isStarted() && getRemainingTime() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS <= 0;
    }

    /**
     * Check if the quiz should be filtered for students (because it hasn't ended yet)
     * @return true if quiz should be filtered, false otherwise
     */
    @JsonIgnore
    public Boolean shouldFilterForStudents() {
        return !isStarted() || isSubmissionAllowed();
    }

    /**
     * Check if the quiz is valid. This means, the quiz needs a title, a valid duration,
     * at least one question, and all questions must be valid
     *
     * @return true if the quiz is valid, otherwise false
     */
    @JsonIgnore
    public Boolean isValid() {
        // check title
        if (getTitle() == null || getTitle().equals("")){
            return false;
        }

        // check duration
        if (getDuration() == null || getDuration() < 0) {
            return false;
        }

        // check questions
        if (getQuestions() == null || getQuestions().isEmpty()) {
            return false;
        }
        for (Question question : getQuestions()) {
            if (!question.isValid()) {
                return false;
            }
        }

        // passed all checks
        return true;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    /**
     * 1. replace the old Question-List with the new one
     * 2. recalculate the PointCounters in quizPointStatistic
     *
     * @param questions the List of Question objects which will be set
     * @return this QuizExercise-object
     */
    public QuizExercise questions(List<Question> questions) {
        this.questions = questions;
        //correct the associated quizPointStatistic implicitly
        recalculatePointCounters();
        return this;
    }

    /**
     * 1. add the new Question object to the Question-List
     * 2. add backward relation in the question-object
     * 3. recalculate the PointCounters in quizPointStatistic
     *
     * @param question the new Question object which will be added
     * @return this QuizExercise-object
     */
    public QuizExercise addQuestions(Question question) {
        this.questions.add(question);
        question.setExercise(this);
        //correct the associated quizPointStatistic implicitly
        recalculatePointCounters();
        return this;
    }

    /**
     * 1. remove the given Question object in the Question-List
     * 2. remove backward relation in the question-object
     * 3. recalculate the PointCounters in quizPointStatistic
     *
     * @param question the Question object which should be removed
     * @return this QuizExercise-object
     */
    public QuizExercise removeQuestions(Question question) {
        this.questions.remove(question);
        question.setExercise(null);
        //correct the associated quizPointStatistic implicitly
        recalculatePointCounters();
        return this;
    }

    /**
     * 1. replace the old Question-List with the new one
     * 2. recalculate the PointCounters in quizPointStatistic
     *
     * @param questions the List of Question objects which will be set
     */
    public void setQuestions(List<Question> questions) {
        this.questions = questions;
        if (questions != null) {
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
        } else if (shouldFilterForStudents()) {
            filterForStudentsDuringQuiz();
        }
    }

    /**
     * set all sensitive information to null, so no info gets leaked to students through json
     */
    public void filterSensitiveInformation() {
        setQuizPointStatistic(null);
        setQuestions(new ArrayList<>());
    }

    /**
     * filter out information about correct answers
     */
    public void filterForStudentsDuringQuiz() {
        // filter out statistics
        setQuizPointStatistic(null);

        // filter out statistics, explanations, and any information about correct answers
        // from all questions (so students can't find them in the JSON while answering the quiz)
        for (Question question : this.getQuestions()) {
            question.filterForStudentsDuringQuiz();
        }
    }

    /**
     * filter out information about correct answers
     */
    public void filterForStatisticWebsocket() {

        // filter out  explanations, and any information about correct answers
        // from all questions (so students can't find them in the JSON while answering the quiz)
        for (Question question : this.getQuestions()) {
            question.filterForStatisticWebsocket();
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
        // iterate through all questions of this quiz
        for (Question question : getQuestions()) {
            // search for submitted answer for this question
            SubmittedAnswer submittedAnswer = quizSubmission.getSubmittedAnswerForQuestion(question);
            if (submittedAnswer != null) {
                score += question.scoreForAnswer(submittedAnswer);
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
    public Question findQuestionById (Long questionId) {

        if (questionId != null) {
            // iterate through all questions of this quiz
            for (Question question : questions) {
                // return question if the IDs are equal
                if (question.getId().equals(questionId)) {
                    return question;
                }
            }
        }
        return null;
    }

    @Override
    public Participation findRelevantParticipation(List<Participation> participations) {
        for (Participation participation : participations) {
            if (participation.getExercise().equals(this)) {
                // in quiz exercises we don't care about the ParticipationState
                // => return the first participation we find
                return participation;
            }
        }
        return null;
    }

    @Override
    public Result findLatestRelevantResult(Participation participation) {
        if (shouldFilterForStudents()) {
            // results are never relevant before quiz has ended => return null
            return null;
        } else {
            // only rated results are considered relevant
            Result latestRatedResult = null;
            for (Result result : participation.getResults()) {
                if (result.isRated() && (latestRatedResult == null || latestRatedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestRatedResult = result;
                }
            }
            return latestRatedResult;
        }
    }

    /**
     * undo all changes which are not allowed after the dueDate ( dueDate, releaseDate, question.points, adding Questions and Answers)
     *
     * @param originalQuizExercise the original QuizExercise object, which will be compared with this quizExercise
     *
     */
    public void undoUnallowedChanges ( QuizExercise originalQuizExercise){

        //reset unchangeable attributes: ( dueDate, releaseDate, question.points)
        this.setDueDate(originalQuizExercise.getDueDate());
        this.setReleaseDate(originalQuizExercise.getReleaseDate());

        //remove added Questions, which are not allowed to be added
        Set<Question> addedQuestions = new HashSet<>();

        //check every question
        for (Question question : questions) {
            //check if the question were already in the originalQuizExercise -> if not it's an added question
            if (originalQuizExercise.getQuestions().contains(question)) {
                // find original unchanged question
                Question originalQuestion = originalQuizExercise.findQuestionById(question.getId());
                //reset score (not allowed to change)
                question.setScore(originalQuestion.getScore());
                //correct invalid = null to invalid = false
                if (question.isInvalid() == null) {
                    question.setInvalid(false);
                }
                //reset invalid if the question is already invalid
                question.setInvalid(question.isInvalid()
                    || (originalQuestion.isInvalid() != null && originalQuestion.isInvalid()));

                //undo all not allowed changes in the answers of the Question
                question.undoUnallowedChanges(originalQuestion);

            } else {
                // question is added (not allowed), mark question for remove
                addedQuestions.add(question);
            }
        }
        // remove all added questions
        questions.removeAll(addedQuestions);
    }

    /**
     * check if an update of the Results and Statistics is necessary after the re-evaluation of this quiz
     *
     * @param originalQuizExercise the original QuizExercise object, which will be compared with this quizExercise
     *
     * @return a boolean which is true if an update is necessary and false if not
     */
    public boolean checkIfRecalculationIsNecessary (QuizExercise originalQuizExercise){

        boolean updateOfResultsAndStatisticsNecessary = false;

        //check every question
        for (Question question : questions) {
            //check if the question were already in the originalQuizExercise
            if (originalQuizExercise.getQuestions().contains(question)) {
                // find original unchanged question
                Question originalQuestion = originalQuizExercise.findQuestionById(question.getId());

                // check if a question is set invalid or if the scoringType has changed
                // if true an update of the Statistics and Results is necessary
                updateOfResultsAndStatisticsNecessary = updateOfResultsAndStatisticsNecessary ||
                    (question.isInvalid() && originalQuestion.isInvalid() == null) ||
                    (question.isInvalid() && !originalQuestion.isInvalid()) ||
                    !question.getScoringType().equals(originalQuestion.getScoringType());

                // check if the question-changes make an update of the statistics and results necessary
                updateOfResultsAndStatisticsNecessary = updateOfResultsAndStatisticsNecessary ||
                    question.isUpdateOfResultsAndStatisticsNecessary(originalQuestion);
            }
        }
        // check if an question was deleted (not allowed added quistions are not relevant)
        // if true an update of the Statistics and Results is necessary
        if (questions.size() != originalQuizExercise.getQuestions().size()) {
            updateOfResultsAndStatisticsNecessary = true;
        }
        return updateOfResultsAndStatisticsNecessary;
    }

    /**
     * Get the maximum total score for this quiz
     *
     * @return the sum of all the questions' maximum scores
     */
    @JsonView(QuizView.During.class)
    public Integer getMaxTotalScore() {
        int maxScore = 0;
        // iterate through all questions of this quiz and add up the score
        if (questions != null && Hibernate.isInitialized(questions)) {
            for (Question question : getQuestions()) {
                maxScore += question.getScore();
            }
        }
        return maxScore;
    }

    @Override
    public Double getMaxScore() {
        //TODO: this is just a temporary solution for legacy exercises, in the future we could run a script to enter these
        //values into the database and assumee that maxScore is always set, then the method getMaxTotalScore() could be removed
        Double score = super.getMaxScore();
        if (score != null) {
            return score;
        }
        else if (questions != null && Hibernate.isInitialized(questions)) {
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
        return "QuizExercise{" +
            "id=" + getId() +
            ", title='" + getTitle() + "'" +
            ", description='" + getDescription() + "'" +
            ", explanation='" + getExplanation() + "'" +
            ", randomizeQuestionOrder='" + isRandomizeQuestionOrder() + "'" +
            ", allowedNumberOfAttempts='" + getAllowedNumberOfAttempts() + "'" +
            ", isVisibleBeforeStart='" + isIsVisibleBeforeStart() + "'" +
            ", isOpenForPractice='" + isIsOpenForPractice() + "'" +
            ", isPlannedToStart='" + isIsPlannedToStart() + "'" +
            ", duration='" + getDuration() + "'" +
            "}";
    }

    /**
     * Constructor.
     *
     * 1. generate associated QuizPointStatistic implicitly
     */
    public QuizExercise() {
        //creates the associated quizPointStatistic implicitly
        quizPointStatistic = new QuizPointStatistic();
        quizPointStatistic.setQuiz(this);
    }

    /**
     * correct the associated quizPointStatistic implicitly
     *
     * 1. add new PointCounters for new Scores
     * 2. delete old PointCounters if the score is no longer contained
     */
    private void recalculatePointCounters() {
        if (quizPointStatistic == null || !Hibernate.isInitialized(quizPointStatistic)) {
            return;
        }

        double quizScore = getMaxTotalScore();

        //add new PointCounter
        for(double i = 0.0 ; i <= quizScore; i++) {  // for variable ScoreSteps change: i++ into: i= i + scoreStep
            quizPointStatistic.addScore(new Double(i));
        }
        //delete old PointCounter
        Set<PointCounter> pointCounterToDelete = new HashSet<>();
        for (PointCounter pointCounter : quizPointStatistic.getPointCounters()) {
            if (pointCounter.getId() != null) {                                                                                        // for variable ScoreSteps add:
                if(pointCounter.getPoints() > quizScore || pointCounter.getPoints() < 0 || questions == null  || questions.isEmpty()/*|| (pointCounter.getPoints()% scoreStep) != 0*/) { ;
                    pointCounterToDelete.add(pointCounter);
                    pointCounter.setQuizPointStatistic(null);
                }
            }
        }
        quizPointStatistic.getPointCounters().removeAll(pointCounterToDelete);
    }
}
