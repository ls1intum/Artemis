package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serializable;
import java.util.List;

/**
 * Structured output of the PLANNING phase (plan Section 2.4, "plan-then-execute").
 * Produced by a dedicated LLM call via {@code BeanOutputConverter} (the established Hyperion pattern,
 * see HyperionQuizQuestionGenerationService for reference usage) and stored on the {@link VariantJob}.
 * Becomes the agent's system-prompt contract in the TRANSFORMING/REPAIRING phases (Section 2.5).
 *
 * Must be {@link Serializable}: it lives inside the Hazelcast-backed job record.
 *
 * @param variantTitle     the LLM-generated title for the variant — NOT client-supplied (Sections 2.4 and 5.1).
 *                             Essential for domain changes ("Bank Account Ledger" → "Cargo Bay Inventory").
 *                             The short name / project key is derived from it deterministically in PROVISIONING,
 *                             with suffix retry (-V2, -V3) on collision (Section 6, row 1).
 * @param problemStatement the rewritten problem statement produced during PLANNING (Section 3, PLANNING row)
 * @param intendedChanges  ordered list of concrete intended changes, e.g. "rename `BankAccount` → `CargoBay`
 *                             across all repos", "remove task 3 and tests `testSortDescending*`" (Section 2.4)
 * @param invariants       invariants to preserve, e.g. "grading semantics unchanged: same number of tasks and
 *                             weights", "test names referenced in problem statement tasks must exist in test repo"
 *                             (Section 2.4). Fed to the semantic verifier gate (Section 2.6, step 3).
 */
public record ChangePlan(String variantTitle, String problemStatement, List<String> intendedChanges, List<String> invariants) implements Serializable {

    // TODO (Sonnet): Verify this record round-trips through Spring AI's BeanOutputConverter (JSON schema generation
    // from the record components) — add @JsonPropertyDescription-style descriptions if the converter supports them so
    // the planner prompt does not need to duplicate field semantics. See plan Section 2.4 for the exact field contract.

    // TODO (Sonnet): Enforce validity on deserialization: variantTitle non-blank, problemStatement non-blank,
    // intendedChanges non-empty. A malformed planner output is returned TO THE MODEL as the error and re-prompted
    // at most 2 times, then the job goes to FAILED (plan Section 6, "Malformed planner/tool output" row).
}
