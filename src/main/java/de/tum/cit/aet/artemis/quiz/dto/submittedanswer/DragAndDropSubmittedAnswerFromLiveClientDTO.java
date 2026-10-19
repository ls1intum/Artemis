package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "drag-and-drop" }, defaultValue = "drag-and-drop"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DragAndDropSubmittedAnswerFromLiveClientDTO(EntityIdRefDTO quizQuestion, Set<DragAndDropMappingFromLiveClientDTO> mappings)
        implements SubmittedAnswerFromLiveClientDTO {
}
