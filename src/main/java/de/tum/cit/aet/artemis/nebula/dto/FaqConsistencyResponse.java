package de.tum.cit.aet.artemis.nebula.dto;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public record FaqConsistencyResponse(@NotNull boolean consistent, @Nullable List<String> inconsistencies, @Nullable List<String> suggestions, @Nullable String improvement) {
}
