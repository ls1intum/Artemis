package de.tum.cit.aet.artemis.quiz.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.validation.Valid;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.quiz.config.QuizView;

@Entity
public abstract class AbstractQuizSubmission extends Submission {

    @Column(name = "score_in_points")
    @JsonView(QuizView.After.class)
    private Double scoreInPoints;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    @Valid
    private Set<SubmittedAnswer> submittedAnswers = new HashSet<>();

    public Double getScoreInPoints() {
        return scoreInPoints;
    }

    public AbstractQuizSubmission scoreInPoints(Double scoreInPoints) {
        this.scoreInPoints = scoreInPoints;
        return this;
    }

    public void setScoreInPoints(Double scoreInPoints) {
        this.scoreInPoints = scoreInPoints;
    }

    public Set<SubmittedAnswer> getSubmittedAnswers() {
        return submittedAnswers;
    }

    public void setSubmittedAnswers(Set<SubmittedAnswer> submittedAnswers) {
        this.submittedAnswers = submittedAnswers;
    }

    public AbstractQuizSubmission submittedAnswers(Set<SubmittedAnswer> submittedAnswers) {
        this.submittedAnswers = submittedAnswers;
        return this;
    }

    public AbstractQuizSubmission addSubmittedAnswers(SubmittedAnswer submittedAnswer) {
        this.submittedAnswers.add(submittedAnswer);
        submittedAnswer.setSubmission(this);
        return this;
    }

    public AbstractQuizSubmission removeSubmittedAnswers(SubmittedAnswer submittedAnswer) {
        this.submittedAnswers.remove(submittedAnswer);
        submittedAnswer.setSubmission(null);
        return this;
    }

    /**
     * Filters the sensitive quiz submission information for exams, if the results are not published or the user is not an instructor
     * It sets the {@link #scoreInPoints} of the submission to null.
     * Additionally, it calls {@link SubmittedAnswer#filterOutCorrectAnswers()} for every submitted answer.
     *
     * @param examResultsPublished flag indicating if the results are published, see {@link Exam#resultsPublished()}
     * @param isAtLeastInstructor  flag indicating if the user has instructor privileges
     */
    public void filterForExam(boolean examResultsPublished, boolean isAtLeastInstructor) {
        if (!(examResultsPublished || isAtLeastInstructor)) {
            this.setScoreInPoints(null);
            // Dynamic binding will call the right overridden method for different question types
            this.getSubmittedAnswers().forEach(SubmittedAnswer::filterOutCorrectAnswers);
        }
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
     * @param quizQuestions the quiz questions of the {@link QuizExercise} this submission belongs to. These questions are needed to calculate the score for each submitted answer.
     */
    public void calculateAndUpdateScores(List<QuizQuestion> quizQuestions) {
        // set scores for all questions
        for (QuizQuestion quizQuestion : quizQuestions) {
            // search for submitted answer for this quizQuestion
            SubmittedAnswer submittedAnswer = getSubmittedAnswerForQuestion(quizQuestion);
            if (submittedAnswer != null) {
                submittedAnswer.setScoreInPoints(quizQuestion.scoreForAnswer(submittedAnswer));
            }
        }
        // set total score
        setScoreInPoints(getScoreInPoints(quizQuestions));
    }

    /**
     * Get the score for this submission as the number of points
     *
     * @param quizQuestions the quiz questions of the submission
     * @return the resulting score
     */
    public Double getScoreInPoints(List<QuizQuestion> quizQuestions) {
        double score = 0.0;
        // iterate through all quizQuestions of this quiz
        for (QuizQuestion quizQuestion : quizQuestions) {
            // search for submitted answer for this quizQuestion
            SubmittedAnswer submittedAnswer = getSubmittedAnswerForQuestion(quizQuestion);
            if (submittedAnswer != null) {
                score += quizQuestion.scoreForAnswer(submittedAnswer);
            }
        }
        return score;
    }

    @Override
    @JsonIgnore
    public boolean isEmpty() {
        return submittedAnswers == null || submittedAnswers.isEmpty();
    }
}
