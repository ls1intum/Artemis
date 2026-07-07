package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The submission-side selection of a {@link MultipleChoiceSubmittedAnswer}: the ids of the answer options the student selected. Stored inside the
 * {@code submitted_answer.selection}
 * JSON column (see {@link SubmittedAnswerSelection}), replacing the former {@code multiple_choice_submitted_answer_selected_options} join table. Mirrors
 * {@link DragAndDropSubmittedAnswerSelection}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class MultipleChoiceSubmittedAnswerSelection implements SubmittedAnswerSelection {

    @JsonProperty("selectedOptionIds")
    private List<Long> selectedOptionIds = new ArrayList<>();

    public List<Long> getSelectedOptionIds() {
        return selectedOptionIds;
    }

    public void setSelectedOptionIds(List<Long> selectedOptionIds) {
        this.selectedOptionIds = selectedOptionIds != null ? selectedOptionIds : new ArrayList<>();
    }
}
