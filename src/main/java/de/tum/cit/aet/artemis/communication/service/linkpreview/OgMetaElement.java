package de.tum.cit.aet.artemis.communication.service.linkpreview;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents single og meta element.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record OgMetaElement(String property, String content, String extraData) {

    private static final String EXTRA_DATA_SEPARATOR = ":";

    OgMetaElement(String property, String content) {
        this(property.contains(EXTRA_DATA_SEPARATOR) ? property.split(EXTRA_DATA_SEPARATOR)[0] : property, content,
                property.contains(EXTRA_DATA_SEPARATOR) ? property.split(EXTRA_DATA_SEPARATOR)[1] : null);
    }

    public boolean hasExtraData() {
        return extraData != null;
    }
}
