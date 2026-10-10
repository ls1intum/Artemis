package de.tum.cit.aet.artemis.aiworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Coordinator reservation bound to a worker incarnation, image and workload capability. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExecutionClaimDTO(ExecutionIdentityDTO identity, String imageDigest, WorkloadCapabilityDTO capability) {
}
