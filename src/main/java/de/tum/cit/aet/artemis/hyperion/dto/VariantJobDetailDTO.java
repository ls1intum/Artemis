package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.service.variants.VariantJob;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantJobPhase;

/**
 * Full job view for reopening the generation modal in monitor mode,
 * GET /api/hyperion/variant-jobs/{jobId} (the modal shows the full inspection view).
 *
 * @param job         the summary view
 * @param stepOutputs per-phase output histories backing the expandable step panels, oldest first: rendered
 *                        plan, provisioned exercise id, per-attempt transform summaries and diffs-of-record,
 *                        one verification report per attempt — earlier failures stay visible after a later
 *                        success (debugging aid)
 * @param request     the original generation request — the modal's "what is being adapted" chips
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VariantJobDetailDTO(VariantJobDTO job, Map<VariantJobPhase, List<StepOutputDTO>> stepOutputs, VariantGenerationRequestDTO request) implements Serializable {

    /**
     * Client-facing projection of one phase's StepOutput.
     *
     * @param summary collapsed panel header text
     * @param detail  expanded panel body (markdown/plain text; already truncated server-side)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StepOutputDTO(String summary, String detail) implements Serializable {
    }

    /**
     * Maps the Hazelcast job record including step-output histories (EnumMap keeps pipeline/phase order).
     *
     * @param job the job record
     * @return the DTO
     */
    public static VariantJobDetailDTO of(VariantJob job) {
        Map<VariantJobPhase, List<StepOutputDTO>> stepOutputs = new EnumMap<>(VariantJobPhase.class);
        job.getStepOutputs().forEach((phase, outputs) -> stepOutputs.put(phase, outputs.stream().map(output -> new StepOutputDTO(output.summary(), output.detail())).toList()));
        return new VariantJobDetailDTO(VariantJobDTO.of(job), stepOutputs, job.getRequest());
    }
}
