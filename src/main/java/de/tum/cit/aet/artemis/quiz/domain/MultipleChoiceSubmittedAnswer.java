package de.tum.cit.aet.artemis.quiz.domain;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A MultipleChoiceSubmittedAnswer.
 */
@Entity
@DiscriminatorValue(value = "MC")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MultipleChoiceSubmittedAnswer extends SubmittedAnswer {

    // No @Cache here on purpose. A second-level cache with NONSTRICT_READ_WRITE used to produce partial / stale selected-option collections
    // under concurrent activity (autosave, evaluation) on a clustered setup, see Artemis issue #12574. Selected options are small and
    // rarely re-read for the same submission, so always fetching from the database is both simpler and deterministic.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "multiple_choice_submitted_answer_selected_options", joinColumns = @JoinColumn(name = "multiple_choice_submitted_answers_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "selected_options_id", referencedColumnName = "id"))
    private Set<AnswerOption> selectedOptions = new HashSet<>();

    public Set<AnswerOption> getSelectedOptions() {
        return selectedOptions;
    }

    public void setSelectedOptions(Set<AnswerOption> answerOptions) {
        this.selectedOptions = answerOptions;
    }

    public void addSelectedOptions(AnswerOption answerOption) {
        this.selectedOptions.add(answerOption);
    }

    /**
     * Check if the given answer option is selected in this submitted answer
     *
     * @param answerOption the answer option to check for
     * @return true if the answer option is selected, false otherwise
     */
    public boolean isSelected(AnswerOption answerOption) {
        // search for this answer option in the selected answer options
        for (AnswerOption selectedOption : getSelectedOptions()) {
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
            Set<AnswerOption> selectedOptionsToDelete = new HashSet<>();
            for (AnswerOption answerOption : this.getSelectedOptions()) {
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
    @Override
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
        return getSelectedOptions().stream().map(DomainObject::getId).collect(Collectors.toSet());
    }

}
