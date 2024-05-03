package de.tum.in.www1.artemis.domain.quiz;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A MultipleChoiceSubmittedAnswer.
 */
@Entity
@DiscriminatorValue(value = "MC")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MultipleChoiceSubmittedAnswer extends SubmittedAnswer {

    @Transient
    private Set<AnswerOptionDTO> selectedOptions = new HashSet<>();

    public Set<AnswerOptionDTO> getSelectedOptions() {
        return this.selectedOptions;
    }

    public void addSelectedOptions(AnswerOptionDTO answerOption) {
        this.selectedOptions.add(answerOption);
    }

    public void setSelectedOptions(Set<AnswerOptionDTO> answerOptions) {
        this.setSelection(answerOptions);
        this.selectedOptions = answerOptions;
    }

    /**
     * Check if the given answer option is selected in this submitted answer
     *
     * @param answerOption the answer option to check for
     * @return true if the answer option is selected, false otherwise
     */
    public boolean isSelected(AnswerOptionDTO answerOption) {
        // search for this answer option in the selected answer options
        for (AnswerOptionDTO selectedOption : getSelectedOptions()) {
            if (Objects.equals(selectedOption.getId(), answerOption.getId())) {
                // this answer option is selected => we can stop searching
                return true;
            }
        }
        // we didn't find the answer option => it wasn't selected
        return false;
    }

    /**
     * Check if answerOptions were deleted and delete reference to in selectedOptions
     *
     * @param question the changed question with the answerOptions
     */
    private void checkAndDeleteSelectedOptions(MultipleChoiceQuestion question) {

        if (question != null) {
            // Check if an answerOption was deleted and delete reference to in selectedOptions
            Set<AnswerOptionDTO> selectedOptionsToDelete = new HashSet<>();
            for (AnswerOptionDTO answerOption : this.getSelectedOptions()) {
                if (!question.getAnswerOptions().contains(answerOption)) {
                    selectedOptionsToDelete.add(answerOption);
                }
            }
            this.getSelectedOptions().removeAll(selectedOptionsToDelete);
        }
    }

    /**
     * Delete all references to question and answers if the question was changed
     *
     * @param quizExercise the changed quizExercise-object
     */
    public void checkAndDeleteReferences(QuizExercise quizExercise) {

        if (!quizExercise.getQuizQuestions().contains(getQuizQuestion())) {
            setQuizQuestion(null);
            selectedOptions = null;
        }
        else {
            // find same quizQuestion in quizExercise
            QuizQuestion quizQuestion = quizExercise.findQuestionById(getQuizQuestion().getId());

            // Check if an answerOption was deleted and delete reference to in selectedOptions
            checkAndDeleteSelectedOptions((MultipleChoiceQuestion) quizQuestion);
        }
    }

    @Override
    public String toString() {
        return "MultipleChoiceSubmittedAnswer{" + "id=" + getId() + "}";
    }

    public Set<Long> toSelectedIds() {
        return getSelectedOptions().stream().map(AnswerOptionDTO::getId).collect(Collectors.toSet());
    }
}
