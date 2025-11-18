package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionDTO(long id, long entityId, @Nullable String entityName, @Nullable String title, ZonedDateTime creationDate, IrisChatMode chatMode) {

}
