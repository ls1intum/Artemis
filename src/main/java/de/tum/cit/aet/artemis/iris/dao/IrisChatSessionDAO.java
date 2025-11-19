package de.tum.cit.aet.artemis.iris.dao;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionDAO(@NonNull IrisChatSession session, long entityId, @Nullable String entityName) {

}
