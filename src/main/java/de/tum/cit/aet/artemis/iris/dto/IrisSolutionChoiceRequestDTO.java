package de.tum.cit.aet.artemis.iris.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisSolutionChoice;

/** A choice must identify the offer it answers. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisSolutionChoiceRequestDTO(@NotNull @Positive Long offerMessageId, @NotNull IrisSolutionChoice choice) {
}
