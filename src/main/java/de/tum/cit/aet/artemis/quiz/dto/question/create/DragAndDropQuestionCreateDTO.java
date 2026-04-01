package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
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
        DragAndDropQuestion question = new DragAndDropQuestion();
        question.setTitle(title);
        question.setText(text);
        question.setHint(hint);
        question.setExplanation(explanation);
        question.setPoints(points);
        question.setScoringType(scoringType);
        question.setRandomizeOrder(randomizeOrder != null ? randomizeOrder : Boolean.FALSE);
        question.setBackgroundFilePath(backgroundFilePath);

        List<DropLocation> locations = dropLocations.stream().map(DropLocationCreateDTO::toDomainObject).toList();
        List<DragItem> items = dragItems.stream().map(DragItemCreateDTO::toDomainObject).toList();
        question.setDropLocations(locations);
        question.setDragItems(items);

        // Resolve mappings using DTO tempIDs to connect to the created domain objects.
        // tempID stays in the DTO layer — the entity objects themselves don't have tempID set.
        Map<Long, DragItem> tempToDragItem = new HashMap<>();
        for (int i = 0; i < dragItems.size(); i++) {
            tempToDragItem.put(dragItems.get(i).tempID(), items.get(i));
        }
        Map<Long, DropLocation> tempToDropLocation = new HashMap<>();
        for (int i = 0; i < dropLocations.size(); i++) {
            tempToDropLocation.put(dropLocations.get(i).tempID(), locations.get(i));
        }

        List<DragAndDropMapping> mappings = correctMappings.stream().map(m -> {
            DragItem dragItem = tempToDragItem.get(m.dragItemTempId());
            DropLocation dropLocation = tempToDropLocation.get(m.dropLocationTempId());
            if (dragItem == null || dropLocation == null) {
                throw new BadRequestAlertException("Could not resolve drag and drop mappings", "quizExercise", "invalidMappings");
            }
            DragAndDropMapping mapping = new DragAndDropMapping();
            mapping.setDragItem(dragItem);
            mapping.setDropLocation(dropLocation);
            return mapping;
        }).toList();
        question.setCorrectMappings(mappings);
        return question;
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
        // Generate stable tempIDs for items: use real id if persisted, otherwise generate a unique one.
        // This is needed for import where entities may not have DB IDs yet.
        long tempIdCounter = 1;
        Map<DragItem, Long> dragItemTempIds = new HashMap<>();
        for (DragItem item : question.getDragItems()) {
            dragItemTempIds.put(item, item.getId() != null ? item.getId() : tempIdCounter++);
        }
        Map<DropLocation, Long> dropLocationTempIds = new HashMap<>();
        for (DropLocation loc : question.getDropLocations()) {
            dropLocationTempIds.put(loc, loc.getId() != null ? loc.getId() : tempIdCounter++);
        }

        List<DragItemCreateDTO> itemDTOs = question.getDragItems().stream().map(di -> new DragItemCreateDTO(dragItemTempIds.get(di), di.getText(), di.getPictureFilePath()))
                .toList();
        List<DropLocationCreateDTO> locationDTOs = question.getDropLocations().stream()
                .map(dl -> new DropLocationCreateDTO(dropLocationTempIds.get(dl), dl.getPosX(), dl.getPosY(), dl.getWidth(), dl.getHeight())).toList();
        List<DragAndDropMappingCreateDTO> mappingDTOs = question.getCorrectMappings().stream()
                .map(m -> new DragAndDropMappingCreateDTO(dragItemTempIds.get(m.getDragItem()), dropLocationTempIds.get(m.getDropLocation()))).toList();

        return new DragAndDropQuestionCreateDTO(question.getTitle(), question.getText(), question.getHint(), question.getExplanation(), question.getPoints(),
                question.getScoringType(), question.isRandomizeOrder(), question.getBackgroundFilePath(), locationDTOs, itemDTOs, mappingDTOs);
    }
}
