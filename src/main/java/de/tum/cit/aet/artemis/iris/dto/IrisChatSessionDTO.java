package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionDTO(@NotNull long id, @NotNull long entityId, @Nullable String entityName, ZonedDateTime creationDate, IrisChatMode chatMode) {

}
