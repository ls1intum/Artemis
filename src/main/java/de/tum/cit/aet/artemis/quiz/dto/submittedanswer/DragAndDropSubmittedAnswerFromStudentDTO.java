package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.DragAndDropMappingReEvaluateDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "drag-and-drop" }, defaultValue = "drag-and-drop"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropSubmittedAnswerFromStudentDTO(@NotNull Long questionId, @NotNull List<@Valid DragAndDropMappingReEvaluateDTO> mappings)
        implements SubmittedAnswerFromStudentDTO {

    public static DragAndDropSubmittedAnswerFromStudentDTO of(DragAndDropSubmittedAnswer submittedAnswer) {
        List<DragAndDropMappingReEvaluateDTO> mappings = submittedAnswer.getMappings().stream().map(DragAndDropMappingReEvaluateDTO::of).toList();
        return new DragAndDropSubmittedAnswerFromStudentDTO(submittedAnswer.getQuizQuestion().getId(), mappings);
    }
}
