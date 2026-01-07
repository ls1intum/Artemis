package de.tum.cit.aet.artemis.programming.service.hades.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/*
 * Record for a build job in Hades
 * This record wraps a build request for Hades. It contains the name of the job, the metadata, the timestamp, the priority, and the steps.
 * The steps are a list of HadesBuildStepDTOs which contain the actual build commands and the respective docker image.
 * The metadata is a hashmap containing key-value pairs for the metadata which should be shared between all build steps.
 * The API Specification for Hades can be found here: https://github.com/Mtze/hades/blob/main/shared/payload/payload.go
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HadesBuildJobDTO(String name, HashMap<String, String> metadata, String timestamp, Integer priority, List<HadesBuildStepDTO> steps) implements Serializable {

    public static HadesBuildJobDTO create(String name, HashMap<String, String> metadata, String timestamp, Integer priority, List<HadesBuildStepDTO> steps) {
        if (steps == null) {
            steps = List.of();  // Assigning an empty list if null
        }
        return new HadesBuildJobDTO(name, metadata, timestamp, priority, steps);
    }

    @Override
    public String toString() {
        return "HadesBuildJobDTO{" + "name='" + name + '\'' + ", metadata=" + metadata + ", timestamp='" + timestamp + '\'' + ", priority=" + priority + ", steps=" + steps + '}';
    }
}
