package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "multiple-choice" }, defaultValue = "multiple-choice"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record MultipleChoiceSubmittedAnswerFromLiveClientDTO(EntityIdRefDTO quizQuestion, Set<EntityIdRefDTO> selectedOptions) implements SubmittedAnswerFromLiveClientDTO {
}
