package de.tum.cit.aet.artemis.programming.service.hades.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Record for a build job in Hades
 * This record wraps a build request for Hades. It contains the name of a job, volumes, metadata, timestamp, priority, and steps.
 * The steps are a list of HadesBuildStepDTOs.
 * The metadata is a hashmap containing key-value pairs for the metadata which should be shared between all build steps.
 * The API Specification for Hades can be found here: <a href="https://github.com/ls1intum/hades/blob/main/shared/payload/payload.go">...</a>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HadesBuildJobDTO(@NotBlank String name, List<VolumeDTO> volumes, HashMap<String, String> metadata, String timestamp, Integer priority,
        @NotEmpty List<HadesBuildStepDTO> steps) implements Serializable {

    public record VolumeDTO(String name, EmptyDirDTO emptyDir) {
    }

    public record EmptyDirDTO() {
    }

    public HadesBuildJobDTO(String name, HashMap<String, String> metadata, String timestamp, Integer priority, List<HadesBuildStepDTO> steps) {
        if (steps == null) {
            steps = List.of();
        }

        List<VolumeDTO> volumes = List.of(new VolumeDTO("shared", new EmptyDirDTO()));
        this(name, volumes, metadata, timestamp, priority, steps);
    }
}
