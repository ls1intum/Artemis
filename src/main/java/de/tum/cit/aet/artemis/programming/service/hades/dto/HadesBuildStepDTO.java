package de.tum.cit.aet.artemis.programming.service.hades.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This record wraps a build step for Hades. It contains the id and name of a step, volume mounts, working dir, metadata, docker image and a script.
 * The metadata is a hashmap containing key-value pairs for the metadata used by the specific build step.
 * Additional shared metadata (between all steps of one job) can be specified in the HadesBuildJobDTO.
 * The API Specification for Hades can be found here: https://github.com/ls1intum/hades/blob/main/shared/payload/payload.go
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HadesBuildStepDTO(Integer id, String name, String image, List<VolumeMount> volumeMounts, String workingDir, HashMap<String, String> metadata, String script)
        implements Serializable {

    public record VolumeMount(String name, String mountPath) {
    }

    // Clone Step constructor
    public HadesBuildStepDTO(Integer id, String name, String image, List<VolumeMount> volumeMounts, String workingDir, HashMap<String, String> metadata) {
        this(id, name, image, volumeMounts, workingDir, metadata, "");
    }

    // Execute Step constructor
    public HadesBuildStepDTO(Integer id, String name, String image, String script) {
        this(id, name, image, new ArrayList<>(), "", new HashMap<>(), script);
    }

    @Override
    public String toString() {
        return "HadesBuildStepDTO{" + "id=" + id + ", name='" + name + '\'' + ", image='" + image + '\'' + ", volumeMounts=" + volumeMounts + ", workingDir='" + workingDir + '\''
                + ", metadata=" + metadata + ", script='" + script + '\'' + '}';
    }
}
