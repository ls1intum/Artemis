package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for an inferred competency from the standardized competency catalog.
 *
 * @param knowledgeAreaShortTitle Short title of the knowledge area (e.g., "AL",
 *                                    "SE")
 * @param competencyTitle         Title of the matched competency from catalog
 * @param competencyVersion       Version of the competency in the catalog
 * @param catalogSourceId         Source ID from the catalog
 * @param taxonomyLevel           Bloom's taxonomy level required for this
 *                                    exercise
 * @param confidence              Confidence score (0.0 to 1.0)
 * @param rank                    Rank (1-5) among inferred competencies
 * @param evidence                Short evidence bullets from problem statement
 * @param whyThisMatches          Instructor-friendly explanation of the match
 * @param isLikelyPrimary         True if this is the primary competency
 * @param relatedTaskNames        Names of exercise tasks that exercise this
 *                                    competency (mapped by LLM)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record InferredCompetencyDTO(String knowledgeAreaShortTitle, String competencyTitle, String competencyVersion, Long catalogSourceId, String taxonomyLevel, Double confidence,
        Integer rank, List<String> evidence, String whyThisMatches, Boolean isLikelyPrimary, List<String> relatedTaskNames) {
}
