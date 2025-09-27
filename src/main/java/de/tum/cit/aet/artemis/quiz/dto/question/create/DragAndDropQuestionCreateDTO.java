package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropQuestionCreateDTO(@NotEmpty String title, String text, String hint, String explanation, double points, @NotNull ScoringType scoringType,
        boolean randomizeOrder, String backgroundFilePath, @NotEmpty List<DropLocationCreateDTO> dropLocations, @NotEmpty List<DragItemCreateDTO> dragItems,
        @NotEmpty List<DragAndDropMappingCreateDTO> correctMappings) implements QuizQuestionCreateDTO {

    public DragAndDropQuestion toDomainObject() {
        DragAndDropQuestion dragAndDropQuestion = new DragAndDropQuestion();
        dragAndDropQuestion.setTitle(title);
        dragAndDropQuestion.setText(text);
        dragAndDropQuestion.setHint(hint);
        dragAndDropQuestion.setExplanation(explanation);
        dragAndDropQuestion.setPoints(points);
        dragAndDropQuestion.setScoringType(scoringType);
        dragAndDropQuestion.setRandomizeOrder(randomizeOrder);
        dragAndDropQuestion.setBackgroundFilePath(backgroundFilePath);

        List<de.tum.cit.aet.artemis.quiz.domain.DropLocation> locations = dropLocations.stream().map(DropLocationCreateDTO::toDomainObject).toList();
        List<de.tum.cit.aet.artemis.quiz.domain.DragItem> items = dragItems.stream().map(DragItemCreateDTO::toDomainObject).toList();
        List<de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping> mappings = correctMappings.stream().map(DragAndDropMappingCreateDTO::toDomainObject).toList();
        dragAndDropQuestion.setDropLocations(locations);
        dragAndDropQuestion.setDragItems(items);
        dragAndDropQuestion.setCorrectMappings(mappings);
        return dragAndDropQuestion;
    }
}
