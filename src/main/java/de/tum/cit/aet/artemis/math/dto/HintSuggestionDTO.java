package de.tum.cit.aet.artemis.math.dto;

import java.util.List;

import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.grader.HintSuggestion;

/**
 * Serializable view of a {@link HintSuggestion} returned by
 * {@code POST /api/math/exercises/{exerciseId}/hints}.
 *
 * @param ruleId        the rule the student should apply
 * @param path          the position in the current tree where it applies
 * @param previewResult the tree that would result from applying the rule
 * @param rationale     short human-readable explanation ({@code null} if none)
 */
public record HintSuggestionDTO(String ruleId, List<Integer> path, MathNode previewResult, String rationale) {

    /**
     * @param hint the grader suggestion to project
     * @return a DTO mirroring the suggestion
     */
    public static HintSuggestionDTO of(HintSuggestion hint) {
        return new HintSuggestionDTO(hint.ruleId(), hint.path(), hint.previewResult(), hint.rationale());
    }
}
