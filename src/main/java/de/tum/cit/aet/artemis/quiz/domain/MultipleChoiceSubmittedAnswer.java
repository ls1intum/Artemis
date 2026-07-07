package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A MultipleChoiceSubmittedAnswer.
 * <p>
 * The selected answer options are stored inside the {@code submitted_answer.selection} JSON column (as a list of answer-option ids in a
 * {@link MultipleChoiceSubmittedAnswerSelection}) instead of the former {@code multiple_choice_submitted_answer_selected_options} join table. The public
 * {@code getSelectedOptions()} / {@code setSelectedOptions()} accessors keep their original signatures/shape: they resolve the stored ids against the owning question so the
 * REST/websocket wire format (nested {@code selectedOptions} objects) is preserved. Mirrors {@link DragAndDropSubmittedAnswer}.
 */
@Entity
@DiscriminatorValue(value = "MC")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MultipleChoiceSubmittedAnswer extends SubmittedAnswer {

    private MultipleChoiceSubmittedAnswerSelection mcSelection() {
        if (getSelection() instanceof MultipleChoiceSubmittedAnswerSelection multipleChoiceSelection) {
            return multipleChoiceSelection;
        }
        MultipleChoiceSubmittedAnswerSelection created = new MultipleChoiceSubmittedAnswerSelection();
        setSelection(created);
        return created;
    }

    private MultipleChoiceQuestion multipleChoiceQuestion() {
        return getQuizQuestion() instanceof MultipleChoiceQuestion question ? question : null;
    }

    /**
     * The selected answer options resolved into objects against the owning question. Built on demand from the scalar-id selection stored in the JSON column. Mutating the returned
     * set does not affect the stored selection — use {@link #addSelectedOptions} / {@link #setSelectedOptions} instead.
     *
     * @return the resolved selected answer options
     */
    public Set<AnswerOption> getSelectedOptions() {
        Set<AnswerOption> result = new HashSet<>();
        if (!(getSelection() instanceof MultipleChoiceSubmittedAnswerSelection selection)) {
            return result;
        }
        MultipleChoiceQuestion question = multipleChoiceQuestion();
        if (question == null) {
            return result;
        }
        for (Long optionId : selection.getSelectedOptionIds()) {
            AnswerOption option = question.findAnswerOptionById(optionId);
            if (option != null) {
                result.add(option);
            }
        }
        return result;
    }

    /**
     * Replaces the selected options, storing them as scalar ids in the JSON selection.
     *
     * @param answerOptions the selected answer options whose ids are stored
     */
    public void setSelectedOptions(Set<AnswerOption> answerOptions) {
        List<Long> ids = new ArrayList<>();
        if (answerOptions != null) {
            for (AnswerOption option : answerOptions) {
                if (option.getId() != null) {
                    ids.add(option.getId());
                }
            }
        }
        mcSelection().setSelectedOptionIds(ids);
    }

    public void addSelectedOptions(AnswerOption answerOption) {
        if (answerOption.getId() != null && !mcSelection().getSelectedOptionIds().contains(answerOption.getId())) {
            mcSelection().getSelectedOptionIds().add(answerOption.getId());
        }
    }

    /**
     * Check if the given answer option is selected in this submitted answer
     *
     * @param answerOption the answer option to check for
     * @return true if the answer option is selected, false otherwise
     */
    public boolean isSelected(AnswerOption answerOption) {
        if (answerOption == null || answerOption.getId() == null || !(getSelection() instanceof MultipleChoiceSubmittedAnswerSelection selection)) {
            return false;
        }
        return selection.getSelectedOptionIds().contains(answerOption.getId());
    }

    /**
     * Delete all references to question and answers if the question was changed
     *
     * @param quizExercise the changed quizExercise-object
     */
    @Override
    public void checkAndDeleteReferences(QuizExercise quizExercise) {
        if (getQuizQuestion() == null || !quizExercise.getQuizQuestions().contains(getQuizQuestion())) {
            setQuizQuestion(null);
            setSelection(null);
            return;
        }
        // Check if an answerOption was deleted and remove it from the stored selection
        if (quizExercise.findQuestionById(getQuizQuestion().getId()) instanceof MultipleChoiceQuestion question
                && getSelection() instanceof MultipleChoiceSubmittedAnswerSelection selection) {
            Set<Long> answerOptionIds = new HashSet<>();
            for (AnswerOption answerOption : question.getAnswerOptions()) {
                answerOptionIds.add(answerOption.getId());
            }
            selection.getSelectedOptionIds().removeIf(optionId -> !answerOptionIds.contains(optionId));
        }
    }

    @Override
    public String toString() {
        return "MultipleChoiceSubmittedAnswer{" + "id=" + getId() + "}";
    }

    public Set<Long> toSelectedIds() {
        if (!(getSelection() instanceof MultipleChoiceSubmittedAnswerSelection selection)) {
            return new HashSet<>();
        }
        return new HashSet<>(selection.getSelectedOptionIds());
    }

}
