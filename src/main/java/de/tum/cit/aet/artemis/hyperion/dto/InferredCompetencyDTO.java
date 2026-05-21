package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for an inferred learning goal from problem statement analysis.
 *
 * @param knowledgeAreaShortTitle   Short title of the knowledge area (e.g., "AL", "SE")
 * @param competencyTitle           AI-inferred learning goal title (NOT a copy of course competency or catalog title)
 * @param competencyVersion         Version of the closest catalog competency
 * @param catalogSourceId           Source ID from the catalog
 * @param taxonomyLevel             Bloom's taxonomy level required for this exercise
 * @param confidence                Confidence score (0.0 to 1.0)
 * @param rank                      Rank (1-5) among inferred learning goals
 * @param evidence                  Short evidence bullets from problem statement
 * @param whyThisMatches            Instructor-friendly explanation of the match
 * @param isLikelyPrimary           True if this is the primary learning goal
 * @param relatedTaskNames          Names of exercise tasks that exercise this learning goal (mapped by LLM)
 * @param matchedCourseCompetencyId ID of the matching existing course competency, or null if none matches
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record InferredCompetencyDTO(String knowledgeAreaShortTitle, String competencyTitle, String competencyVersion, Long catalogSourceId, String taxonomyLevel, Double confidence,
        Integer rank, List<String> evidence, String whyThisMatches, Boolean isLikelyPrimary, List<String> relatedTaskNames, Long matchedCourseCompetencyId) {
}
