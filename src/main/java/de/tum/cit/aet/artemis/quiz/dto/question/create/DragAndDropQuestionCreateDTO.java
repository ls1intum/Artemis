package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropQuestionCreateDTO(@NotEmpty String title, String text, String hint, String explanation, double points, @NotNull ScoringType scoringType,
        boolean randomizeOrder, String backgroundFilePath, @NotEmpty List<DropLocationCreateDTO> dropLocations, @NotEmpty List<DragItemCreateDTO> dragItems,
        @NotEmpty List<DragAndDropMappingCreateDTO> correctMappings) implements QuizQuestionCreateDTO {
}
