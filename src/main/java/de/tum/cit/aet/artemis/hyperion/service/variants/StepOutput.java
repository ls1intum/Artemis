package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serializable;
import java.time.Instant;

/**
 * Output of one completed pipeline phase, stored on the job as {@code Map<VariantJobPhase, StepOutput>}.
 * Rendered in the generation modal as an expandable panel per finished step so instructors can inspect what
 * the LLM planned and did — during the run and after completion.
 *
 * @param summary    short human-readable summary of what the phase did (shown collapsed in the panel header)
 * @param detail     the full output: rendered ChangePlan for PLANNING, provisioned exercise id for PROVISIONING,
 *                       per-attempt transform summaries and diffs-of-record for TRANSFORMING/REPAIRING,
 *                       rendered VerificationReport for VERIFYING
 * @param recordedAt when the phase completed
 */
public record StepOutput(String summary, String detail, Instant recordedAt) implements Serializable {

    // `detail` is kept bounded by the pipeline's truncate() (100 KB + "[truncated]" marker) before storage, since
    // Hazelcast entries are replicated; the full agent transcript goes to the log, not the job record.
}
