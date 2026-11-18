package de.tum.cit.aet.artemis.aiworker.dto;

import java.time.Instant;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Bounded workload data is decoded once by the explicitly registered workload handler. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExecutionAssignmentDTO(ExecutionIdentityDTO identity, WorkloadCapabilityDTO capability, Instant deadline, String imageDigest, String payload) {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("sha256:[a-f0-9]{64}");

    public ExecutionAssignmentDTO {
        if (identity == null || capability == null || deadline == null || imageDigest == null || !IMAGE_PATTERN.matcher(imageDigest).matches() || payload == null
                || payload.isBlank() || payload.length() > 48 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid execution assignment");
        }
    }
}
