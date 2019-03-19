package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A QuizSubmission.
 */
@Entity
@DiscriminatorValue(value="Q")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class QuizSubmission extends Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "score_in_points")
    @JsonView(QuizView.After.class)
    private Double scoreInPoints;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
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

    /**
     * Remove all values for scoreInPoints in this submission and all its submitted answers
     */
    public void removeScores() {
        for (SubmittedAnswer submittedAnswer : getSubmittedAnswers()) {
            submittedAnswer.setScoreInPoints(null);
        }
        setScoreInPoints(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuizSubmission quizSubmission = (QuizSubmission) o;
        if (quizSubmission.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), quizSubmission.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "QuizSubmission{" +
            "id=" + getId() +
            ", scoreInPoints='" + getScoreInPoints() + "'" +
            "}";
    }
}
