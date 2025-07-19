package de.tum.cit.aet.artemis.iris.dao;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionDAO(@NotNull IrisChatSession session, long entityId, @Nullable String entityName) {

}
