package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/*
 * DTO for a build job in Hades
 * This DTO wraps a build request for Hades. It contains the name of the job, the metadata, the timestamp, the priority and the steps.
 * The steps are a list of HadesBuildStepDTOs which contain the actual build commands and the respective docker image.
 * The metadata is a hashmap containing key-value pairs for the metadata which should be shared between all build steps.
 * The API Specification for Hades can be found here: https://github.com/Mtze/hades/blob/main/shared/payload/payload.go
 */
public class HadesBuildJobDTO implements Serializable {

    private String name;

    /* The shared metadata (key-value pairs) which are injected into all container environments of all build steps */
    private HashMap<String, String> metadata;

    /* The timestamp of the build job sumbission */
    private String timestamp;

    /* The priority of the build job - used to select more important build jobs first */
    private Integer priority;

    /* The list of build steps which should be executed sequentially */
    private ArrayList<HadesBuildStepDTO> steps;

    public HadesBuildJobDTO() {
        // empty constructor for jackson
    }

    public HadesBuildJobDTO(String name, HashMap<String, String> metadata, String timestamp, Integer priority, ArrayList<HadesBuildStepDTO> steps) {
        this.name = name;
        this.metadata = metadata;
        this.timestamp = timestamp;
        this.priority = priority;
        this.steps = steps;
    }

    public String getName() {
        return name;
    }

    public Object getMetadata() {
        return metadata;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public Integer getPriority() {
        return priority;
    }

    public Object getSteps() {
        return steps;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMetadata(HashMap<String, String> metadata) {
        this.metadata = metadata;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public void setSteps(ArrayList<HadesBuildStepDTO> steps) {
        this.steps = steps;
    }

    @Override
    public String toString() {
        return "HadesBuildJobDTO{" + "name='" + name + '\'' + ", metadata=" + metadata + ", timestamp='" + timestamp + '\'' + ", priority=" + priority + ", steps=" + steps + '}';
    }
}
