package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisSolutionChoice;

/** Server-created user content recording an accepted offer. Never accepted as ordinary client content. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisSolutionChoiceDTO(String type, long offerMessageId, IrisSolutionChoice choice) {
}
