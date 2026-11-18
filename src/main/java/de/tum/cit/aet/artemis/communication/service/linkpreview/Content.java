package de.tum.cit.aet.artemis.communication.service.linkpreview;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the content of an Open Graph meta tag.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record Content(String value, Map<String, String> extraData) {

    Content {
        // make sure extra data is initialized
        extraData = extraData != null ? extraData : new HashMap<>();
    }

    void addExtraData(String extraData, String value) {
        this.extraData.put(extraData, value);
    }
}
