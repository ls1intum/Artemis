package de.tum.cit.aet.artemis.nebula.dto;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqConsistencyResponseDTO(@NotNull boolean consistent, @Nullable List<String> inconsistencies, @Nullable String improvement, @Nullable List<Long> faqIds) {
}
