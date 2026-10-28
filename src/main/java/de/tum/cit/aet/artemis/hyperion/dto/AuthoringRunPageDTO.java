package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cursor page; an empty authorized page can still carry a cursor when access to intervening courses was revoked. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AuthoringRunPageDTO(@JsonInclude @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AuthoringRunDTO> runs, Long nextBeforeId) {
}
