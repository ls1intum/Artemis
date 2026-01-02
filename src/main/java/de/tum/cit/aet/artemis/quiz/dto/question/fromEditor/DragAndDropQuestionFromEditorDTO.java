package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

/**
 * DTO for drag and drop questions in the editor context.
 * Supports both creating new questions (id is null) and updating existing questions (id is non-null).
 *
 * @param id                 the ID of the question, null for new questions
 * @param title              the title of the question
 * @param text               the question text
 * @param hint               the hint for the question
 * @param explanation        the explanation for the question
 * @param points             the points for the question
 * @param scoringType        the scoring type
 * @param randomizeOrder     whether to randomize drag item order
 * @param backgroundFilePath the background image file path
 * @param dropLocations      the list of drop locations
 * @param dragItems          the list of drag items
 * @param correctMappings    the list of correct mappings
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropQuestionFromEditorDTO(Long id, @NotEmpty String title, String text, String hint, String explanation, @NotNull @Positive Double points,
        @NotNull ScoringType scoringType, Boolean randomizeOrder, String backgroundFilePath, @NotEmpty List<@Valid DropLocationFromEditorDTO> dropLocations,
        @NotEmpty List<@Valid DragItemFromEditorDTO> dragItems, @NotEmpty List<@Valid DragAndDropMappingFromEditorDTO> correctMappings) implements QuizQuestionFromEditorDTO {

    /**
     * Creates a DragAndDropQuestionFromEditorDTO from the given DragAndDropQuestion domain object.
     *
     * @param question the question to convert
     * @return the corresponding DTO
     */
    public static DragAndDropQuestionFromEditorDTO of(DragAndDropQuestion question) {
        List<DropLocationFromEditorDTO> locationDTOs = question.getDropLocations().stream().map(DropLocationFromEditorDTO::of).toList();
        List<DragItemFromEditorDTO> itemDTOs = question.getDragItems().stream().map(DragItemFromEditorDTO::of).toList();
        List<DragAndDropMappingFromEditorDTO> mappingDTOs = question.getCorrectMappings().stream().map(DragAndDropMappingFromEditorDTO::of).toList();
        return new DragAndDropQuestionFromEditorDTO(question.getId(), question.getTitle(), question.getText(), question.getHint(), question.getExplanation(), question.getPoints(),
                question.getScoringType(), question.isRandomizeOrder(), question.getBackgroundFilePath(), locationDTOs, itemDTOs, mappingDTOs);
    }

    /**
     * Creates a new DragAndDropQuestion domain object from this DTO.
     *
     * @return a new DragAndDropQuestion domain object
     */
    @Override
    public DragAndDropQuestion toDomainObject() {
        DragAndDropQuestion question = new DragAndDropQuestion();
        question.setId(id);
        question.setTitle(title);
        question.setText(text);
        question.setHint(hint);
        question.setExplanation(explanation);
        question.setPoints(points);
        question.setScoringType(scoringType);
        question.setRandomizeOrder(randomizeOrder != null ? randomizeOrder : Boolean.FALSE);
        question.setBackgroundFilePath(backgroundFilePath);

        List<DropLocation> locations = dropLocations.stream().map(DropLocationFromEditorDTO::toDomainObject).toList();
        List<DragItem> items = dragItems.stream().map(DragItemFromEditorDTO::toDomainObject).toList();
        List<DragAndDropMapping> mappings = correctMappings.stream().map(DragAndDropMappingFromEditorDTO::toDomainObject).toList();
        question.setDropLocations(locations);
        question.setDragItems(items);
        question.setCorrectMappings(mappings);
        return question;
    }
}
