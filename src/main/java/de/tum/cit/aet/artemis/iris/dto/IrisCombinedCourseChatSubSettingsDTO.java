package de.tum.cit.aet.artemis.iris.dto;

import java.util.SortedSet;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedCourseChatSubSettingsDTO(boolean enabled, Integer rateLimit, Integer rateLimitTimeframeHours, @Nullable SortedSet<String> allowedVariants,
        @Nullable String selectedVariant) {

}
