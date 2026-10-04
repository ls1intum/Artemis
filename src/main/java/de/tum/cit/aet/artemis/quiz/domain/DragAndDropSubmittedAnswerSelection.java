package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The submission-side selection of a {@link DragAndDropSubmittedAnswer}: the list of drag-item/drop-location pairs the student submitted. Stored inside the
 * {@code submitted_answer.selection} JSON column (see {@link SubmittedAnswerSelection}).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class DragAndDropSubmittedAnswerSelection implements SubmittedAnswerSelection {

    @JsonProperty("maps")
    private List<DragAndDropMappingSelection> mappings = new ArrayList<>();

    public List<DragAndDropMappingSelection> getMappings() {
        return mappings;
    }

    public void setMappings(List<DragAndDropMappingSelection> mappings) {
        this.mappings = mappings != null ? mappings : new ArrayList<>();
    }
}
