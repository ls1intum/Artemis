package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.service.variants.VariantJobPhase;

/**
 * Full job view for reopening the generation modal in monitor mode,
 * GET /api/hyperion/variant-jobs/{jobId} (plan Sections 5.1, 5.4: "modal = full inspection").
 *
 * @param job         the summary view
 * @param stepOutputs per-phase outputs backing the expandable step panels (plan Section 2.4): rendered plan,
 *                        provisioned exercise id, per-attempt transform summaries and diffs-of-record,
 *                        verification reports
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VariantJobDetailDTO(VariantJobDTO job, Map<VariantJobPhase, StepOutputDTO> stepOutputs) implements Serializable {

    /**
     * Client-facing projection of one phase's StepOutput.
     *
     * @param summary collapsed panel header text
     * @param detail  expanded panel body (markdown/plain text; already truncated server-side)
     */
    public record StepOutputDTO(String summary, String detail) implements Serializable {
    }

    // TODO (Sonnet): Static factory `of(VariantJob job)` mapping the Hazelcast record incl. stepOutputs; keep
    // ordering by phase ordinal so the client renders the timeline in pipeline order (plan Section 5.4).
    // Unused-parameter note for downstream: `List` import is for a possible per-attempt output list if
    // TRANSFORMING outputs are kept per attempt rather than overwritten — decide and clean up.
}
