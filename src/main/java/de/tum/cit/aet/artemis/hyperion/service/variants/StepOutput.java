package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serializable;
import java.time.Instant;

/**
 * Output of one completed pipeline phase, stored on the job as {@code Map<VariantJobPhase, StepOutput>}
 * (plan Section 2.4). Rendered in the generation modal as an expandable panel per finished step so
 * instructors can inspect what the LLM planned and did — during the run and after completion (Section 5.4).
 * Also the raw material for the thesis evaluation ("did the executed diff match the plan?", Section 7).
 *
 * @param summary    short human-readable summary of what the phase did (shown collapsed in the panel header)
 * @param detail     the full output: rendered ChangePlan for PLANNING, provisioned exercise id for PROVISIONING,
 *                       per-attempt transform summaries and diffs-of-record for TRANSFORMING/REPAIRING,
 *                       rendered VerificationReport for VERIFYING (Section 2.4)
 * @param recordedAt when the phase completed
 */
public record StepOutput(String summary, String detail, Instant recordedAt) implements Serializable {

    // TODO (Sonnet): Keep `detail` bounded — Hazelcast entries are replicated; truncate diffs/transcripts to a sane
    // max (e.g. 100 KB) with an explicit "[truncated]" marker. The full agent transcript goes to the log, not the
    // job record (plan Section 2.5 "transcript logging" lands in the agent loop runner, not here).
}
