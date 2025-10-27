package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropQuestionReEvaluateDTO(@NotNull Long id, @NotBlank String title, String text, String hint, String explanation, @NotNull ScoringType scoringType,
        @NotNull Boolean randomizeOrder, @NotNull Boolean invalid, @NotEmpty List<@Valid DropLocationReEvaluateDTO> dropLocations,
        @NotEmpty List<@Valid DragItemReEvaluateDTO> dragItems, @NotEmpty List<@Valid DragAndDropMappingReEvaluateDTO> correctMappings) implements QuizQuestionReEvaluateDTO {

    /**
     * Builds a DragAndDropQuestionReEvaluateDTO from the given domain entity.
     * Copies the question metadata (title, text, hint, explanation, scoring type, randomize order, invalid) and converts all drop locations, drag items, and correct mappings to
     * their corresponding DTOs.
     *
     * @param dragAndDropQuestion the source DragAndDropQuestion to convert (must not be null)
     * @return a DTO representing the provided drag-and-drop question
     */
    public static DragAndDropQuestionReEvaluateDTO of(DragAndDropQuestion dragAndDropQuestion) {
        return new DragAndDropQuestionReEvaluateDTO(dragAndDropQuestion.getId(), dragAndDropQuestion.getTitle(), dragAndDropQuestion.getText(), dragAndDropQuestion.getHint(),
                dragAndDropQuestion.getExplanation(), dragAndDropQuestion.getScoringType(), dragAndDropQuestion.isRandomizeOrder(), dragAndDropQuestion.isInvalid(),
                dragAndDropQuestion.getDropLocations().stream().map(DropLocationReEvaluateDTO::of).toList(),
                dragAndDropQuestion.getDragItems().stream().map(DragItemReEvaluateDTO::of).toList(),
                dragAndDropQuestion.getCorrectMappings().stream().map(DragAndDropMappingReEvaluateDTO::of).toList());
    }
}
