package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropQuestionCreateDTO(String title, String text, String hint, String explanation, double points, ScoringType scoringType, boolean randomizeOrder,
        String backgroundFilePath, List<DropLocationCreateDTO> dropLocations, List<DragItemCreateDTO> dragItems, List<DragAndDropMappingCreateDTO> correctMappings)
        implements QuizQuestionCreateDTO {
}
