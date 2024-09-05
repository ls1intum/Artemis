package de.tum.in.www1.artemis.service.connectors.pyris.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisEventDTO<T>(T event, String eventType) {

}
