package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for chat history messages retrieved from ChatMemory.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChatHistoryMessageDTO(

        @NotNull @NotBlank String role,

        @NotNull @NotBlank String content

) {
}
