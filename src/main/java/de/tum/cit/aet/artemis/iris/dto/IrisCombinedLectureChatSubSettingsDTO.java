package de.tum.cit.aet.artemis.iris.dto;

import java.util.SortedSet;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedLectureChatSubSettingsDTO(boolean enabled, Integer rateLimit, Integer rateLimitTimeframeHours, @Nullable String customInstructions,
        @Nullable SortedSet<String> allowedVariants, @Nullable String selectedVariant) {

}
