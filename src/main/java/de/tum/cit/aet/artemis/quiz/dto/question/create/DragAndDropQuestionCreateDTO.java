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

    /**
     * Converts this DTO to a {@link DragAndDropQuestion} domain object.
     * <p>
     * Maps the DTO properties to the corresponding fields in the domain object and transforms the lists
     * of {@link DropLocationCreateDTO}, {@link DragItemCreateDTO}, and {@link DragAndDropMappingCreateDTO}
     * into lists of {@link de.tum.cit.aet.artemis.quiz.domain.DropLocation}, {@link de.tum.cit.aet.artemis.quiz.domain.DragItem},
     * and {@link de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping} objects by invoking their respective
     * {@code toDomainObject} methods.
     *
     * @return the {@link DragAndDropQuestion} domain object with properties and child entities set from this DTO
     */
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
