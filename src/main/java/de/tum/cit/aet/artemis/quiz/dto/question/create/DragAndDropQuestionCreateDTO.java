package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropQuestionCreateDTO(@NotEmpty String title, String text, String hint, String explanation, @NotNull @Positive Double points, @NotNull ScoringType scoringType,
        Boolean randomizeOrder, String backgroundFilePath, @NotEmpty List<@Valid DropLocationCreateDTO> dropLocations, @NotEmpty List<@Valid DragItemCreateDTO> dragItems,
        @NotEmpty List<@Valid DragAndDropMappingCreateDTO> correctMappings) implements QuizQuestionCreateDTO {

    /**
     * Converts this DTO to a {@link DragAndDropQuestion} domain object.
     * <p>
     * Maps the DTO properties to the corresponding fields in the domain object and transforms the lists
     * of {@link DropLocationCreateDTO}, {@link DragItemCreateDTO}, and {@link DragAndDropMappingCreateDTO}
     * into lists of {@link DropLocation}, {@link DragItem},
     * and {@link DragAndDropMapping} objects by invoking their respective
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
        dragAndDropQuestion.setRandomizeOrder(randomizeOrder != null ? randomizeOrder : Boolean.FALSE);
        dragAndDropQuestion.setBackgroundFilePath(backgroundFilePath);

        List<DropLocation> locations = dropLocations.stream().map(DropLocationCreateDTO::toDomainObject).toList();
        List<DragItem> items = dragItems.stream().map(DragItemCreateDTO::toDomainObject).toList();
        List<DragAndDropMapping> mappings = correctMappings.stream().map(DragAndDropMappingCreateDTO::toDomainObject).toList();
        dragAndDropQuestion.setDropLocations(locations);
        dragAndDropQuestion.setDragItems(items);
        dragAndDropQuestion.setCorrectMappings(mappings);
        return dragAndDropQuestion;
    }

    /**
     * Creates a {@link DragAndDropQuestionCreateDTO} from the given {@link DragAndDropQuestion} domain object.
     * <p>
     * Maps the domain object's properties to the corresponding DTO fields and transforms the lists
     * of {@link DropLocation}, {@link DragItem},
     * and {@link DragAndDropMapping} into lists of {@link DropLocationCreateDTO},
     * {@link DragItemCreateDTO}, and {@link DragAndDropMappingCreateDTO} by invoking their respective {@code of} methods.
     *
     * @param question the {@link DragAndDropQuestion} domain object to convert
     * @return the {@link DragAndDropQuestionCreateDTO} with properties and child DTOs set from the domain object
     */
    public static DragAndDropQuestionCreateDTO of(DragAndDropQuestion question) {
        List<DropLocationCreateDTO> locationDTOs = question.getDropLocations().stream().map(DropLocationCreateDTO::of).toList();
        List<DragItemCreateDTO> itemDTOs = question.getDragItems().stream().map(DragItemCreateDTO::of).toList();
        List<DragAndDropMappingCreateDTO> mappingDTOs = question.getCorrectMappings().stream().map(DragAndDropMappingCreateDTO::of).toList();
        return new DragAndDropQuestionCreateDTO(question.getTitle(), question.getText(), question.getHint(), question.getExplanation(), question.getPoints(),
                question.getScoringType(), question.isRandomizeOrder(), question.getBackgroundFilePath(), locationDTOs, itemDTOs, mappingDTOs);
    }
}
