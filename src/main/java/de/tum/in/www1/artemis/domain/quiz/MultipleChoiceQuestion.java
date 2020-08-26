package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.*;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A MultipleChoiceQuestion.
 */
@Entity
@DiscriminatorValue(value = "MC")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeName("multiple-choice")
public class MultipleChoiceQuestion extends QuizQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "question_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<AnswerOption> answerOptions = new ArrayList<>();

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove

    public List<AnswerOption> getAnswerOptions() {
        return answerOptions;
    }

    public void setAnswerOptions(List<AnswerOption> answerOptions) {
        this.answerOptions = answerOptions;
    }

    /**
     * Get answerOption by ID
     *
     * @param answerOptionId the ID of the answerOption, which should be found
     * @return the answerOption with the given ID, or null if the answerOption is not contained in this question
     */
    public AnswerOption findAnswerOptionById(Long answerOptionId) {

        if (answerOptionId != null) {
            // iterate through all answers of this quiz
            for (AnswerOption answer : answerOptions) {
                // return answer if the IDs are equal
                if (answer.getId().equals(answerOptionId)) {
                    return answer;
                }
            }
        }
        return null;
    }

    /**
     * undo all answer-changes which are not allowed (adding Answers)
     *
     * @param originalQuizQuestion the original QuizQuestion-object, which will be compared with this question
     */
    public void undoUnallowedChanges(QuizQuestion originalQuizQuestion) {

        if (originalQuizQuestion instanceof MultipleChoiceQuestion) {
            MultipleChoiceQuestion mcOriginalQuestion = (MultipleChoiceQuestion) originalQuizQuestion;
            undoUnallowedAnswerChanges(mcOriginalQuestion);
        }
    }

    /**
     * undo all answer-changes which are not allowed ( adding Answers)
     *
     * @param originalQuestion the original MultipleChoiceQuestion-object, which will be compared with this question
     */
    private void undoUnallowedAnswerChanges(MultipleChoiceQuestion originalQuestion) {

        // find added Answers, which are not allowed to be added
        Set<AnswerOption> notAllowedAddedAnswers = new HashSet<>();
        // check every answer of the question
        for (AnswerOption answer : this.getAnswerOptions()) {
            // check if the answer were already in the originalQuizExercise -> if not it's an added answer
            if (originalQuestion.getAnswerOptions().contains(answer)) {
                // find original answer
                AnswerOption originalAnswer = originalQuestion.findAnswerOptionById(answer.getId());
                // correct invalid = null to invalid = false
                if (answer.isInvalid() == null) {
                    answer.setInvalid(false);
                }
                // reset invalid answer if it already set to true (it's not possible to set an answer valid again)
                answer.setInvalid(answer.isInvalid() || (originalAnswer.isInvalid() != null && originalAnswer.isInvalid()));
            }
            else {
                // mark the added Answers (adding answers is not allowed)
                notAllowedAddedAnswers.add(answer);
            }
        }
        // remove the added Answers
        this.getAnswerOptions().removeAll(notAllowedAddedAnswers);
    }

    /**
     * check if an update of the Results and Statistics is necessary
     *
     * @param originalQuizQuestion the original QuizQuestion-object, which will be compared with this question
     * @return a boolean which is true if the answer-changes make an update necessary and false if not
     */
    public boolean isUpdateOfResultsAndStatisticsNecessary(QuizQuestion originalQuizQuestion) {
        if (originalQuizQuestion instanceof MultipleChoiceQuestion) {
            MultipleChoiceQuestion mcOriginalQuestion = (MultipleChoiceQuestion) originalQuizQuestion;
            return checkAnswersIfRecalculationIsNecessary(mcOriginalQuestion);
        }
        return false;
    }

    /**
     * check if an update of the Results and Statistics is necessary
     *
     * @param originalQuestion the original MultipleChoiceQuestion-object, which will be compared with this question
     * @return a boolean which is true if the answer-changes make an update necessary and false if not
     */
    private boolean checkAnswersIfRecalculationIsNecessary(MultipleChoiceQuestion originalQuestion) {

        boolean updateNecessary = false;

        // check every answer of the question
        for (AnswerOption answer : this.getAnswerOptions()) {
            // check if the answer were already in the originalQuizExercise
            if (originalQuestion.getAnswerOptions().contains(answer)) {
                // find original answer
                AnswerOption originalAnswer = originalQuestion.findAnswerOptionById(answer.getId());

                // check if an answer is set invalid or if the correctness has changed
                // if true an update of the Statistics and Results is necessary
                if ((answer.isInvalid() && !this.isInvalid() && originalAnswer.isInvalid() == null) || (answer.isInvalid() && !this.isInvalid() && !originalAnswer.isInvalid())
                        || (!(answer.isIsCorrect().equals(originalAnswer.isIsCorrect())))) {
                    updateNecessary = true;
                }
            }
        }
        // check if an answer was deleted (not allowed added answers are not relevant)
        // if true an update of the Statistics and Results is necessary
        if (this.getAnswerOptions().size() < originalQuestion.getAnswerOptions().size()) {
            updateNecessary = true;
        }
        return updateNecessary;
    }

    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

    @Override
    public void filterForStudentsDuringQuiz() {
        super.filterForStudentsDuringQuiz();
        for (AnswerOption answerOption : getAnswerOptions()) {
            answerOption.setIsCorrect(null);
            answerOption.setExplanation(null);
        }
    }

    @Override
    public void filterForStatisticWebsocket() {
        super.filterForStatisticWebsocket();
        for (AnswerOption answerOption : getAnswerOptions()) {
            answerOption.setIsCorrect(null);
            answerOption.setExplanation(null);
        }
    }

    @Override
    public Boolean isValid() {
        // check general validity (using superclass)
        if (!super.isValid()) {
            return false;
        }

        // check answer options
        if (getAnswerOptions() != null) {
            for (AnswerOption answerOption : getAnswerOptions()) {
                if (answerOption.isIsCorrect()) {
                    // at least one correct answer option exists
                    return true;
                }
            }
        }
        // no correct answer option exists
        return false;
    }

    @Override
    public String toString() {
        return "MultipleChoiceQuestion{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", text='" + getText() + "'" + ", hint='" + getHint() + "'" + ", explanation='"
                + getExplanation() + "'" + ", score='" + getScore() + "'" + ", scoringType='" + getScoringType() + "'" + ", randomizeOrder='" + isRandomizeOrder() + "'"
                + ", exerciseTitle='" + ((getExercise() == null) ? null : getExercise().getTitle()) + "'" + "}";
    }

    @Override
    public QuizQuestion copyQuestionId() {
        var question = new MultipleChoiceQuestion();
        question.setId(getId());
        return question;
    }
}
