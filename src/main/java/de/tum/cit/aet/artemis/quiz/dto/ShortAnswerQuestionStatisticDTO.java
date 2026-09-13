package de.tum.cit.aet.artemis.quiz.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "short-answer" }, defaultValue = "short-answer"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionStatisticDTO(Integer participantsRated, Integer participantsUnrated, Integer ratedCorrectCounter, Integer unRatedCorrectCounter,
        Set<ShortAnswerSpotCounterDTO> shortAnswerSpotCounters) implements QuizQuestionStatisticDTO {
}
