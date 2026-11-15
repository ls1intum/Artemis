package de.tum.cit.aet.artemis.communication.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.FaqState;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The new content of an existing FAQ.
 *
 * @param id             the FAQ to update; must equal the faqId path parameter
 * @param questionTitle  the question
 * @param questionAnswer the optional answer
 * @param categories     the optional categories, each a JSON string such as {@code {"category":"Exams","color":"#123456"}}
 * @param faqState       the workflow state; only instructors may update an accepted FAQ or accept one
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateFaqDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The FAQ to update; must equal the faqId path parameter") @Positive long id,
        @NotBlank String questionTitle, @Nullable String questionAnswer,
        @ArraySchema(uniqueItems = true, schema = @Schema(description = "A category serialized as JSON, e.g. {\"category\":\"Exams\",\"color\":\"#123456\"}")) @Nullable Set<String> categories,
        @NotNull FaqState faqState) {
}
