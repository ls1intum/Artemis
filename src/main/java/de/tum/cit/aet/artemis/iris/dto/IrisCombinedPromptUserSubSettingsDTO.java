package de.tum.cit.aet.artemis.iris.dto;

import java.util.SortedSet;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedPromptUserSubSettingsDTO(boolean enabled, @Nullable SortedSet<String> allowedVariants, @Nullable String selectedVariant, @PositiveOrZero int minQuestions,
        @PositiveOrZero int maxQuestions, @PositiveOrZero int timeLimitQuestion, @PositiveOrZero int timeLimitInClass) {

}
