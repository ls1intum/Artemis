package de.tum.in.www1.exerciseapp.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;

/**
 * A QuizExercise.
 */
@Entity
@DiscriminatorValue(value="Q")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class QuizExercise extends Exercise implements Serializable {

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
    private String description;

    @Column(name = "explanation")
    private String explanation;

    @Column(name = "randomize_question_order")
    private Boolean randomizeQuestionOrder;

    @Column(name = "allowed_number_of_attempts")
    private Integer allowedNumberOfAttempts;

    @Column(name = "is_visible_before_start")
    private Boolean isVisibleBeforeStart;

    @Column(name = "is_open_for_practice")
    private Boolean isOpenForPractice;

    @Column(name = "is_planned_to_start")
    private Boolean isPlannedToStart;

    /**
     * The duration of the quiz exercise in seconds
     */
    @Column(name = "duration")
    private Integer duration;

    @OneToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER, orphanRemoval=true)
    @JoinColumn(unique = true)
    private QuizPointStatistic quizPointStatistic;

    @OneToMany(cascade=CascadeType.ALL, fetch=FetchType.EAGER, orphanRemoval=true)
    @OrderColumn
    @JoinColumn(name="exercise_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
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
    public String getType() { return "quiz"; }

    @Override
    public ZonedDateTime getDueDate() {
        return isPlannedToStart ? getReleaseDate().plusSeconds(getDuration()) : null;
    }

    public Long getRemainingTime() {
        return isPlannedToStart ? ChronoUnit.SECONDS.between(ZonedDateTime.now(), getDueDate()) : null;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public QuizExercise questions(List<Question> questions) {
        this.questions = questions;
        //correct the associated quizPointStatistic implicitly
        recalculatePointCounters();
        return this;
    }

    public QuizExercise addQuestions(Question question) {
        this.questions.add(question);
        question.setExercise(this);
        //correct the associated quizPointStatistic implicitly
        recalculatePointCounters();
        return this;
    }

    public QuizExercise removeQuestions(Question question) {
        this.questions.remove(question);
        question.setExercise(null);
        //correct the associated quizPointStatistic implicitly
        recalculatePointCounters();
        return this;
    }

    public void setQuestions(List<Question> questions) {

        this.questions = questions;
        recalculatePointCounters();
    }

    @Override
    public Boolean getIsVisibleToStudents() {
        //TODO: what happens if release date is null?
        return isVisibleBeforeStart || (isPlannedToStart && releaseDate.isBefore(ZonedDateTime.now()));
    }

    /**
     * Get the score for this submission as a number from 0 to 100 (100 being the best possible result)
     * @param quizSubmission the submission that should be evaluated
     * @return the resulting score
     */
    public Long getScoreForSubmission(QuizSubmission quizSubmission) {
        double score = 0.0;
        int maxScore = 0;
        // iterate through all questions of this quiz
        for (Question question : getQuestions()) {
            // add this question's maxScore to the maxScore of the entire quiz
            maxScore += question.getScore();
            // search for submitted answer for this question
            for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
                if (question.getId().longValue() == submittedAnswer.getQuestion().getId().longValue()) {
                    // add points for this submitted answer to the total
                    score += question.scoreForAnswer(submittedAnswer);
                    break;
                }
                // if there is no submitted answer for this question in the submission,
                // the resulting score is 0 (i.e. nothing gets added to the score)
            }
        }
        // map the resulting score to the 0 to 100 scale
        return Math.round(100.0 * score / maxScore);
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
            ", questions='" + getQuestions() + "'" +
            "}";
    }

    public QuizExercise() {
        //creates the associated quizPointStatistic implicitly
        quizPointStatistic = new QuizPointStatistic();
        quizPointStatistic.setQuiz(this);
    }

    /**
     * 1. add up the scores of every question in this quiz
     *
     * @return the sum of these scores
     */
    public int getMaxQuizScore() {
        int quizScore = 0;

        if(questions != null) {
            //calculate Score of the Quiz
            for(Question question: questions) {
                quizScore = quizScore + question.getScore();
            }
        }
        return quizScore;
    }

    /**
     * correct the associated quizPointStatistic implicitly
     *
     * 1. add new PointCounters for new Scores
     * 2. delete old PointCounters if the score is no longer contained
     */
    private void recalculatePointCounters() {

        double quizScore = getMaxQuizScore();

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
