package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.io.Serializable;
import java.util.HashMap;

/*
 * DTO for a build step in Hades
 * This DTO wraps a build step for Hades. It contains the name of the step, the metadata, the docker image and the script.
 * The metadata is a hashmap containing key-value pairs for the metadata used by the specific build step.
 * Additional shared metadata (between all steps of one job can be specified in the HadesBuildJobDTO.
 * The API Specification for Hades can be found here: https://github.com/Mtze/hades/blob/main/shared/payload/payload.go
 */
public class HadesBuildStepDTO implements Serializable {

    /* The id is used to identify and order build steps in a HadesCI Job */
    private Integer id;

    private String name;

    /* The docker image used for the build step */
    private String image;

    /* The metadata (key-value pairs) which are injected into the container environment */
    private HashMap<String, String> metadata;

    /* The script which is executed in the container */
    private String script;

    public HadesBuildStepDTO() {
        // empty constructor for jackson
    }

    public HadesBuildStepDTO(Integer id, String name, String image, HashMap<String, String> metadata, String script) {
        this.id = id;
        this.name = name;
        this.image = image;
        this.metadata = metadata;
        this.script = script;
    }

    public HadesBuildStepDTO(Integer id, String name, String image, String script) {
        this.id = id;
        this.name = name;
        this.image = image;
        this.metadata = metadata;
        this.script = script;
    }

    public HadesBuildStepDTO(Integer id, String name, String image, HashMap<String, String> metadata) {
        this.id = id;
        this.name = name;
        this.image = image;
        this.metadata = metadata;
        this.script = script;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getImage() {
        return image;
    }

    public HashMap<String, String> getMetadata() {
        return metadata;
    }

    public String getScript() {
        return script;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setMetadata(HashMap<String, String> metadata) {
        this.metadata = metadata;
    }

    public void setScript(String script) {
        this.script = script;
    }

    @Override
    public String toString() {
        return "HadesBuildStepDTO{" + "id=" + id + ", name='" + name + '\'' + ", image='" + image + '\'' + ", metadata=" + metadata + ", script='" + script + '\'' + '}';
    }
}
