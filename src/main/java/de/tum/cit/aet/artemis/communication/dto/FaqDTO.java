package de.tum.cit.aet.artemis.communication.dto;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A persisted FAQ of a course. An empty answer or category set is omitted.
 *
 * @param id             the FAQ ID
 * @param questionTitle  the question
 * @param questionAnswer the answer, omitted when empty
 * @param categories     the categories, each a JSON string such as {@code {"category":"Exams","color":"#123456"}}, omitted when empty
 * @param faqState       the workflow state
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String questionTitle,
        @Nullable String questionAnswer,
        @ArraySchema(uniqueItems = true, schema = @Schema(description = "A category serialized as JSON, e.g. {\"category\":\"Exams\",\"color\":\"#123456\"}")) @Nullable Set<String> categories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FaqState faqState) {

    public FaqDTO(Faq faq) {
        this(faq.getId(), faq.getQuestionTitle(), faq.getQuestionAnswer(), faq.getCategories(), faq.getFaqState());
    }

}
