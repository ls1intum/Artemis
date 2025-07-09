package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionDTO(Long id, Long entityId, ZonedDateTime creationDate, IrisChatMode chatMode) {

}
