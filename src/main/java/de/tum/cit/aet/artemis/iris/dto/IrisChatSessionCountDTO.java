package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionCountDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessions, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long messages) {
}
