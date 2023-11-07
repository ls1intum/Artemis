package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.util.*;

/**
 * Represents a Docker action that can be defined in a {@link Windfile}
 */
public class DockerConfig {

    private String image;

    private String tag;

    private List<String> volumes;

    private List<String> parameters;

    public String getImage() {
        return image;
    }

    private void setImage(String image) {
        this.image = image;
    }

    public String getTag() {
        return tag;
    }

    private void setTag(String tag) {
        this.tag = tag;
    }

    public List<String> getVolumes() {
        return volumes;
    }

    private void setVolumes(List<String> volumes) {
        this.volumes = volumes;
    }

    public List<String> getParameters() {
        return parameters;
    }

    private void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }
}
