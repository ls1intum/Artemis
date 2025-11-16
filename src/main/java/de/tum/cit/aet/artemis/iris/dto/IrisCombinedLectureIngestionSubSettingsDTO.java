package de.tum.cit.aet.artemis.iris.dto;

import java.util.SortedSet;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedLectureIngestionSubSettingsDTO(boolean enabled, boolean autoIngest, @Nullable SortedSet<String> allowedVariants, @Nullable String selectedVariant) {
}
