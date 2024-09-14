package de.tum.cit.aet.artemis.communication.service.linkpreview.ogparser;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents the content of an Open Graph meta tag.
 */
public class Content {

    private final String value;

    private final Map<String, String> extraData;

    Content(String value) {
        this.value = value;
        extraData = new HashMap<>();
    }

    public String getValue() {
        return value;
    }

    public Set<String> getAllExtraData() {
        return extraData.keySet();
    }

    public String getExtraDataValueOf(String extraData) {
        return this.extraData.get(extraData);
    }

    void setExtraData(String extraData, String value) {
        this.extraData.put(extraData, value);
    }
}
