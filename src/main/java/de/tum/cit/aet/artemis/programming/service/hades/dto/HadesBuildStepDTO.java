package de.tum.cit.aet.artemis.programming.service.hades.dto;

import java.io.Serializable;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonInclude;

/*
 * Record for a build step in Hades
 * This record wraps a build step for Hades. It contains the name of the step, the metadata, the docker image and the script.
 * The metadata is a hashmap containing key-value pairs for the metadata used by the specific build step.
 * Additional shared metadata (between all steps of one job) can be specified in the HadesBuildJobDTO.
 * The API Specification for Hades can be found here: https://github.com/Mtze/hades/blob/main/shared/payload/payload.go
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HadesBuildStepDTO(Integer id, String name, String image, HashMap<String, String> metadata, String script) implements Serializable {

    public HadesBuildStepDTO(Integer id, String name, String image, String script) {
        this(id, name, image, new HashMap<>(), script);
    }

    public HadesBuildStepDTO(Integer id, String name, String image, HashMap<String, String> metadata) {
        this(id, name, image, metadata, "");
    }

    @Override
    public String toString() {
        return "HadesBuildStepDTO{" + "id=" + id + ", name='" + name + '\'' + ", image='" + image + '\'' + ", metadata=" + metadata + ", script='" + script + '\'' + '}';
    }
}
