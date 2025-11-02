package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MemirisLearningDTO(@NotNull String id, @NotNull String title, @NotNull String content, @Nullable String reference, @NotNull List<String> memories) {
}
