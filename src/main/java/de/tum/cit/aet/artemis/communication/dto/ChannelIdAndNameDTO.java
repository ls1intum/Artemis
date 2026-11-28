package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A DTO representing a channel which contains only the id and name
 *
 * @param id   id of the channel
 * @param name name of the channel
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChannelIdAndNameDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {
}
