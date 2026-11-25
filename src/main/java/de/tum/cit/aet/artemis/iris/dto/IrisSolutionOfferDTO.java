package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

/** Structured assistant content binding an offer to the context in which it was made. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisSolutionOfferDTO(String type, IrisChatMode chatMode, long entityId) {
}
