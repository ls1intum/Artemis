package de.tum.cit.aet.artemis.iris.dao;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionDAO(IrisChatSession session, long entityId, String entityName) {

}
