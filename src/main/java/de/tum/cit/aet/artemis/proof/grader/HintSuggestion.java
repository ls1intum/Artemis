package de.tum.cit.aet.artemis.proof.grader;

import java.util.List;

import de.tum.cit.aet.artemis.proof.domain.MathNode;

/**
 * A single suggestion for the next step a student could take. Returned by
 * {@link ProofGrader#suggestHints}; surfaced as a popover in the workspace UI.
 *
 * @param ruleId        the rule the student should apply
 * @param path          the position in the current tree where it applies
 * @param previewResult the tree that would result from applying the rule
 * @param rationale     short human-readable explanation ({@code null} if none)
 */
public record HintSuggestion(String ruleId, List<Integer> path, MathNode previewResult, String rationale) {

    public HintSuggestion {
        path = path == null ? List.of() : List.copyOf(path);
    }
}
