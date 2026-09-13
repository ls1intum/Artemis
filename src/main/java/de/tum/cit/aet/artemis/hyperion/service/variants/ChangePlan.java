package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serializable;
import java.util.List;

/**
 * Structured output of the PLANNING phase ("plan-then-execute"). Produced by a dedicated LLM call via
 * {@code BeanOutputConverter} and stored on the {@link VariantJob}; it becomes the agent's system-prompt
 * contract in the TRANSFORMING/REPAIRING phases.
 *
 * Must be {@link Serializable}: it lives inside the Hazelcast-backed job record.
 *
 * @param variantTitle     the LLM-generated title for the variant — not client-supplied, essential for domain
 *                             changes ("Bank Account Ledger" → "Cargo Bay Inventory"). The short name / project
 *                             key is derived from it deterministically in PROVISIONING, with suffix retry on collision.
 * @param problemStatement the rewritten problem statement produced during PLANNING
 * @param intendedChanges  ordered list of concrete intended changes, e.g. "rename `BankAccount` → `CargoBay`
 *                             across all repos", "remove task 3 and tests `testSortDescending*`"
 * @param invariants       invariants to preserve, e.g. "grading semantics unchanged: same number of tasks and
 *                             weights", "test names referenced in problem statement tasks must exist in test repo";
 *                             fed to the semantic verifier gate
 */
public record ChangePlan(String variantTitle, String problemStatement, List<String> intendedChanges, List<String> invariants) implements Serializable {

    // Round-trips through Spring AI's BeanOutputConverter (covered by the ChangePlan unit tests). Validity
    // (non-blank title/statement, non-empty intendedChanges) is enforced by the pipeline's validatePlan, which
    // returns malformed output to the model and re-prompts up to twice before FAILED.
}
