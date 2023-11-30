package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.io.Serializable;
import java.util.HashMap;

public class HadesBuildStepDTO implements Serializable {

    private Integer id;

    private String name;

    private String image;

    private HashMap<String, String> metadata;

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
