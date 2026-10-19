package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;

/**
 * Response shape of a {@link StaticCodeAnalysisCategory}.
 * <p>
 * The bidirectional {@code exercise} back-reference the entity carries is dropped: no client reads it (the grading
 * table binds {@code id}, {@code name}, {@code state}, {@code penalty} and {@code maxPenalty}, the charts join the
 * issue map by {@code name}, and the save request-builder rebuilds its body from {@code id}, {@code penalty},
 * {@code maxPenalty} and {@code state}).
 * <p>
 * {@code NON_EMPTY} mirrors the entity's own annotation, so the wire is unchanged: a {@code null} penalty is already
 * absent today and every consumer copes with that.
 *
 * @param id         the category id
 * @param name       the category name; the charts join the issue statistics by this value
 * @param state      whether the category is inactive, feedback-only or graded
 * @param penalty    the penalty per issue in this category
 * @param maxPenalty the maximum penalty this category can deduct
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StaticCodeAnalysisCategoryDTO(Long id, String name, CategoryState state, Double penalty, Double maxPenalty) {

    /**
     * Converts a category entity into its response representation. The lazy {@code exercise} back-reference is never
     * touched.
     *
     * @param category the category to convert
     * @return the converted DTO
     */
    public static StaticCodeAnalysisCategoryDTO of(StaticCodeAnalysisCategory category) {
        return new StaticCodeAnalysisCategoryDTO(category.getId(), category.getName(), category.getState(), category.getPenalty(), category.getMaxPenalty());
    }
}
