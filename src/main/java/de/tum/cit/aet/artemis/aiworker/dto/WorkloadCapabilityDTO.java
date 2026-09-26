package de.tum.cit.aet.artemis.aiworker.dto;

import java.io.Serializable;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Explicit workload schema and qualified execution profile; never a caller-selected implementation class. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkloadCapabilityDTO(String workload, int version, String profile) implements Serializable {

    private static final Pattern CAPABILITY_PATTERN = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    public WorkloadCapabilityDTO {
        if (workload == null || !CAPABILITY_PATTERN.matcher(workload).matches() || version < 1 || profile == null || !CAPABILITY_PATTERN.matcher(profile).matches()) {
            throw new IllegalArgumentException("Invalid workload capability");
        }
    }
}
