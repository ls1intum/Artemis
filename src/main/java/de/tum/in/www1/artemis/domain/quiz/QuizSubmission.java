package de.tum.in.www1.artemis.domain.quiz;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A QuizSubmission.
 */
@Entity
@DiscriminatorValue(value = "Q")
@JsonTypeName("quiz")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizSubmission extends AbstractQuizSubmission {

    // used to distinguish the type when used in collections (e.g. SearchResultPageDTO --> resultsOnPage)
    public String getSubmissionExerciseType() {
        return "quiz";
    }

    @Column(name = "score_in_points")
    @JsonView(QuizView.After.class)
    private Double scoreInPoints;

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
}
