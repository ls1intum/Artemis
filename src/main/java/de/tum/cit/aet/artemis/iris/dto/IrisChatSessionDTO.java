package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionDTO(Long id, Long entityId, String chatMode, ZonedDateTime creationDate) {
}
