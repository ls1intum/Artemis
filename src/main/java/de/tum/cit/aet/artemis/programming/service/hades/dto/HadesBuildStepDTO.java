package de.tum.cit.aet.artemis.programming.service.hades.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This record wraps a build step for Hades. It contains the id and name of a step, volume mounts, working dir, metadata, docker image and a script.
 * The metadata is a hashmap containing key-value pairs for the metadata used by the specific build step.
 * Additional shared metadata (between all steps of one job) can be specified in the HadesBuildJobDTO.
 * The API Specification for Hades can be found here: https://github.com/ls1intum/hades/blob/main/shared/payload/payload.go
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HadesBuildStepDTO(@Positive @NotNull Integer id, String name, @NotBlank String image, List<VolumeMountDTO> volumeMounts, String workingDir,
        HashMap<String, String> metadata, String script, @JsonProperty("continue_on_error") boolean continueOnError) implements Serializable {

    public record VolumeMountDTO(String name, String mountPath) {
    }
}
