package de.tum.cit.aet.artemis.domain.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A QuizSubmission.
 */
@Entity
@DiscriminatorValue(value = "Q")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizSubmission extends AbstractQuizSubmission {

    @Override
    public String getSubmissionExerciseType() {
        return "quiz";
    }

    // The use of id here is on purpose because @ManyToOne relation cannot be lazily fetched and typically, QuizBatch is not needed when loading QuizSubmission
    @Column(name = "quiz_batch")
    private Long quizBatch;

    public void setQuizBatch(Long quizBatch) {
        this.quizBatch = quizBatch;
    }

    public Long getQuizBatch() {
        return quizBatch;
    }

    @Override
    public String toString() {
        return "QuizSubmission{" + "id=" + getId() + ", scoreInPoints='" + getScoreInPoints() + "'" + "}";
    }

    /**
     * Remove details that should not be sent to the client from the quiz questions.
     */
    public void filterForStudentsDuringQuiz() {
        for (SubmittedAnswer submittedAnswer : getSubmittedAnswers()) {
            if (submittedAnswer.getQuizQuestion() != null) {
                submittedAnswer.getQuizQuestion().filterForStudentsDuringQuiz();
            }
        }
    }
}
