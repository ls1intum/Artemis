package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "short-answer" }, defaultValue = "short-answer"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShortAnswerSubmittedAnswerFromLiveClientDTO(EntityIdRefDTO quizQuestion, Set<@Valid ShortAnswerSubmittedTextFromLiveClientDTO> submittedTexts)
        implements SubmittedAnswerFromLiveClientDTO {
}
