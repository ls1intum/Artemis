package de.tum.in.www1.artemis.domain.quiz;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.Valid;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A QuizSubmission.
 */
@Entity
@DiscriminatorValue(value = "Q")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizSubmission extends Submission {

    @Column(name = "score_in_points")
    @JsonView(QuizView.After.class)
    private Double scoreInPoints;

    @Column(name = "quiz_batch")
    private Long quizBatch;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    @Valid
    private Set<SubmittedAnswer> submittedAnswers = new HashSet<>();

    public Double getScoreInPoints() {
        return scoreInPoints;
    }

    public QuizSubmission scoreInPoints(Double scoreInPoints) {
        this.scoreInPoints = scoreInPoints;
        return this;
    }

    public void setScoreInPoints(Double scoreInPoints) {
        this.scoreInPoints = scoreInPoints;
    }

    public void setQuizBatch(Long quizBatch) {
        this.quizBatch = quizBatch;
    }

    public Long getQuizBatch() {
        return quizBatch;
    }

    public Set<SubmittedAnswer> getSubmittedAnswers() {
        return submittedAnswers;
    }

    public QuizSubmission submittedAnswers(Set<SubmittedAnswer> submittedAnswers) {
        this.submittedAnswers = submittedAnswers;
        return this;
    }

    public QuizSubmission addSubmittedAnswers(SubmittedAnswer submittedAnswer) {
        this.submittedAnswers.add(submittedAnswer);
        submittedAnswer.setSubmission(this);
        return this;
    }

    public QuizSubmission removeSubmittedAnswers(SubmittedAnswer submittedAnswer) {
        this.submittedAnswers.remove(submittedAnswer);
        submittedAnswer.setSubmission(null);
        return this;
    }

    /**
     * Filters the sensitive quiz submission information for exams, if the results are not published or the user is not an instructor
     * It sets the {@link QuizSubmission#setScoreInPoints(Double)} & {@link SubmittedAnswer#setScoreInPoints(Double)} to null for every submitted answer.
     * Additionally it calls {@link SubmittedAnswer#filterOutCorrectAnswers()} dynamically for the correct question type.
     * @param examResultsPublished flag indicating if the results are published, see {@link Exam#resultsPublished()}
     * @param isAtLeastInstructor flag indicating if the user has instructor privileges
     */
    public void filterForExam(boolean examResultsPublished, boolean isAtLeastInstructor) {
        if (!(examResultsPublished || isAtLeastInstructor)) {
            this.setScoreInPoints(null);
            // Dynamic binding will call the right overridden method for different question types
            this.getSubmittedAnswers().forEach(SubmittedAnswer::filterOutCorrectAnswers);
        }
    }

    public void setSubmittedAnswers(Set<SubmittedAnswer> submittedAnswers) {
        this.submittedAnswers = submittedAnswers;
    }

    /**
     * Get the submitted answer to the given quizQuestion
     *
     * @param quizQuestion the quizQuestion that the answer should belong to
     * @return the submitted answer for this quizQuestion (null if this quizQuestion wasn't answered by this submission)
     */
    public SubmittedAnswer getSubmittedAnswerForQuestion(QuizQuestion quizQuestion) {
        for (SubmittedAnswer submittedAnswer : getSubmittedAnswers()) {
            if (quizQuestion.equals(submittedAnswer.getQuizQuestion())) {
                return submittedAnswer;
            }
        }
        return null;
    }

    /**
     * calculates the scores for this submission and all its submitted answers and saves them in scoreInPoints
     *
     * @param quizExercise the quiz this submission belongs to (is needed to have values for isCorrect in answer options)
     */
    public void calculateAndUpdateScores(QuizExercise quizExercise) {
        // set scores for all questions
        for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
            // search for submitted answer for this quizQuestion
            SubmittedAnswer submittedAnswer = getSubmittedAnswerForQuestion(quizQuestion);
            if (submittedAnswer != null) {
                submittedAnswer.setScoreInPoints(quizQuestion.scoreForAnswer(submittedAnswer));
            }
        }
        // set total score
        setScoreInPoints(quizExercise.getScoreInPointsForSubmission(this));
    }

    @Override
    @JsonIgnore
    public boolean isEmpty() {
        return submittedAnswers == null || submittedAnswers.isEmpty();
    }

    @Override
    public String toString() {
        return "QuizSubmission{" + "id=" + getId() + ", scoreInPoints='" + getScoreInPoints() + "'" + "}";
    }
}
