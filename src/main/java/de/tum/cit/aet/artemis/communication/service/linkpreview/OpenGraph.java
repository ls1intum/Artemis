package de.tum.cit.aet.artemis.communication.service.linkpreview;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenGraph is a record that represents the OpenGraph data of a website.
 * It contains a map of all properties and their content.
 * The content is a list of Content objects.
 * The Content object contains the value of the property and extra data.
 * The extra data is a map of all extra data and their values.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record OpenGraph(Map<String, List<Content>> openGraphMap) {

    Content getContentOf(String property) {
        return getContentOf(property, 0);
    }

    /**
     * Returns the content of the property at the given index.
     * If the property does not exist, null is returned.
     *
     * @param property the property
     * @param index    the index
     * @return the content of the property at the given index
     */
    Content getContentOf(String property, int index) {
        if (openGraphMap.get(property) == null) {
            return null;
        }
        if (openGraphMap.get(property).isEmpty()) {
            return null;
        }
        return openGraphMap.get(property).get(index);
    }
}
