package de.tum.cit.aet.artemis.communication.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A new FAQ for a course.
 *
 * @param courseId       the course the FAQ belongs to; must equal the course in the request path
 * @param questionTitle  the question
 * @param questionAnswer the optional answer
 * @param categories     the optional categories, each a JSON string such as {@code {"category":"Exams","color":"#123456"}}
 * @param faqState       the workflow state; only instructors may create an accepted FAQ
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateFaqDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The course of the FAQ; must equal the courseId path parameter") long courseId,
        @NotBlank String questionTitle, @Nullable String questionAnswer,
        @ArraySchema(uniqueItems = true, schema = @Schema(description = "A category serialized as JSON, e.g. {\"category\":\"Exams\",\"color\":\"#123456\"}")) @Nullable Set<String> categories,
        @NotNull FaqState faqState) {

    /**
     * Converts this DTO to a Faq entity.
     *
     * @return a new Faq entity with the data from this DTO
     */
    public Faq toEntity() {
        Faq faq = new Faq();
        faq.setFaqState(this.faqState);
        faq.setCategories(this.categories);
        faq.setQuestionTitle(this.questionTitle);
        faq.setQuestionAnswer(this.questionAnswer);
        return faq;
    }
}
