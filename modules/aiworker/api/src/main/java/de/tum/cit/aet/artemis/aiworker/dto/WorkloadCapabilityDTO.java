package de.tum.cit.aet.artemis.aiworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Explicit workload schema and qualified execution profile; never a caller-selected implementation class. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkloadCapabilityDTO(String workload, int version, String profile) implements java.io.Serializable {

    private static final java.util.regex.Pattern CAPABILITY_PATTERN = java.util.regex.Pattern.compile("[a-z][a-z0-9-]{0,63}");

    public WorkloadCapabilityDTO {
        if (workload == null || !CAPABILITY_PATTERN.matcher(workload).matches() || version < 1 || profile == null || !CAPABILITY_PATTERN.matcher(profile).matches()) {
            throw new IllegalArgumentException("Invalid workload capability");
        }
    }
}
