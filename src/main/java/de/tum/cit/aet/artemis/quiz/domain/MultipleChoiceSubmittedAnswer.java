package de.tum.cit.aet.artemis.quiz.domain;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "multiple_choice_submitted_answer_selected_options", joinColumns = @JoinColumn(name = "multiple_choice_submitted_answers_id"))
    @Column(name = "selected_options_id")
    private Set<Long> selectedOptionIds = new HashSet<>();

    /**
     * Resolves the selected question-scoped option IDs to their JSON-owned answer options for the API contract.
     *
     * @return the selected answer options in deterministic order
     */
    @JsonProperty("selectedOptions")
    public Set<AnswerOption> getSelectedOptions() {
        if (!(getQuizQuestion() instanceof MultipleChoiceQuestion question) || selectedOptionIds == null) {
            return Set.of();
        }
        return selectedOptionIds.stream().map(optionId -> {
            AnswerOption option = question.findAnswerOptionById(optionId);
            if (option == null) {
                throw new IllegalStateException("Multiple-choice submission " + getId() + " references missing answer option " + optionId + " in question " + question.getId());
            }
            return option;
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @JsonProperty("selectedOptions")
    public void setSelectedOptions(Set<AnswerOption> answerOptions) {
        this.selectedOptionIds = answerOptions == null ? null : answerOptions.stream().map(AnswerOption::getId).collect(Collectors.toCollection(HashSet::new));
    }

    public void addSelectedOptions(AnswerOption answerOption) {
        if (selectedOptionIds == null) {
            selectedOptionIds = new HashSet<>();
        }
        selectedOptionIds.add(answerOption.getId());
    }

    @JsonIgnore
    public Set<Long> getSelectedOptionIds() {
        return selectedOptionIds == null ? null : Set.copyOf(selectedOptionIds);
    }

    @JsonIgnore
    public void setSelectedOptionIds(Set<Long> selectedOptionIds) {
        this.selectedOptionIds = selectedOptionIds == null ? null : new HashSet<>(selectedOptionIds);
    }

    /**
     * Check if the given answer option is selected in this submitted answer
     *
     * @param answerOption the answer option to check for
     * @return true if the answer option is selected, false otherwise
     */
    public boolean isSelected(AnswerOption answerOption) {
        // search for this answer option in the selected answer options
        return selectedOptionIds != null && selectedOptionIds.contains(answerOption.getId());
    }

    /**
     * Check if answerOptions were deleted and delete reference to in selectedOptions
     *
     * @param question the changed question with the answerOptions
     */
    private void checkAndDeleteSelectedOptions(MultipleChoiceQuestion question) {

        if (question != null && selectedOptionIds != null) {
            // Check if an answerOption was deleted and delete reference to in selectedOptions
            selectedOptionIds.removeIf(answerOptionId -> question.findAnswerOptionById(answerOptionId) == null);
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
            selectedOptionIds = null;
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
        return selectedOptionIds == null ? null : Set.copyOf(selectedOptionIds);
    }

}
