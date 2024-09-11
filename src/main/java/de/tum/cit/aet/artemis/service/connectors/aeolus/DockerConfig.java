package de.tum.cit.aet.artemis.service.connectors.aeolus;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a Docker configuration that can be defined in a {@link Windfile}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DockerConfig(String image, String tag, List<String> volumes, List<String> parameters) {

    @Override
    public String image() {
        return image != null ? image.trim() : null;
    }

    @Override
    public String tag() {
        return tag != null ? tag.trim() : null;
    }

    /**
     * Returns the full image name including the tag, if a tag is defined within the image, that tag is used
     * instead of the tag
     *
     * @return the full image name including the tag
     */
    @JsonIgnore
    public String getFullImageName() {
        var image = image();
        var tag = tag();
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
