package de.tum.cit.aet.artemis.iris.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMcqResponseDTO(int selectedIndex, boolean submitted, @Nullable Integer questionIndex) {
}
