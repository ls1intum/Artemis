package de.tum.in.www1.artemis.service.linkpreview.ogparser;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenGraph is a class that represents the OpenGraph data of a website.
 * It contains a map of all properties and their content.
 * The content is a list of Content objects.
 * The Content object contains the value of the property and extra data.
 * The extra data is a map of all extra data and their values.
 */
public class OpenGraph {

    private final Map<String, List<Content>> openGraphMap;

    OpenGraph(Map<String, List<Content>> openGraphMap) {
        this.openGraphMap = openGraphMap;
    }

    public Set<String> getAllProperties() {
        return openGraphMap.keySet();
    }

    public Content getContentOf(String property) {
        return getContentOf(property, 0);
    }

    public Content getContentOf(String property, int index) {
        return openGraphMap.get(property).get(index);
    }

    public int getContentSizeOf(String property) {
        return openGraphMap.get(property).size();
    }
}
