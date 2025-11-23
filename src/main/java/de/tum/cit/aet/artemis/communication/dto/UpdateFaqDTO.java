package de.tum.cit.aet.artemis.communication.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.FaqState;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateFaqDTO(@Positive long id, @NotBlank String questionTitle, @Nullable String questionAnswer, @Nullable Set<String> categories, @NotNull FaqState faqState) {
}
