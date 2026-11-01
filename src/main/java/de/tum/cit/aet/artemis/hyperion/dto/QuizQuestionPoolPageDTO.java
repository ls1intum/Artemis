package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO carrying one page of a course's quiz question pool.
 *
 * @param items        the items of the requested page, in pool order; absent when the page is empty
 * @param totalCount   how many items match the request across all pages
 * @param competencies every competency that has items in the pool, for filtering; absent when the pool is empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "One page of a course's quiz question pool")
public record QuizQuestionPoolPageDTO(@NotNull @Schema(description = "The items of the requested page, absent when the page is empty") List<QuizQuestionPoolItemDTO> items,
        @Schema(description = "How many items match the request across all pages", requiredMode = Schema.RequiredMode.REQUIRED) long totalCount,
        @NotNull @Schema(description = "Every competency that has items in the pool, absent when the pool is empty") List<QuizQuestionPoolCompetencyDTO> competencies) {
}
