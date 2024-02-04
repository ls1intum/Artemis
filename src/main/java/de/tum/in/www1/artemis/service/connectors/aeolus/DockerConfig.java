package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.util.List;

/**
 * Represents a Docker configuration that can be defined in a {@link Windfile}
 */
public class DockerConfig {

    private String image;

    private String tag;

    private List<String> volumes;

    private List<String> parameters;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public List<String> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<String> volumes) {
        this.volumes = volumes;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    /**
     * Returns the full image name including the tag, if a tag is defined within the image, that tag is used
     * instead of the tag
     *
     * @return the full image name including the tag
     */
    public String getFullImageName() {
        if (image == null) {
            return null;
        }
        if (tag == null) {
            if (!image.contains(":")) {
                return image + ":" + "latest";
            }
        }
        if (image.contains(":")) {
            return image;
        }
        return image + ":" + tag;
    }
}
