package de.tum.cit.aet.artemis.communication.service.linkpreview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the Open Graph meta tags of a website.
 */
class OgParser {

    private final OgMetaElementHtmlParser ogMetaElementHtmlParser;

    OgParser(OgMetaElementHtmlParser ogMetaElementHtmlParser) {
        this.ogMetaElementHtmlParser = ogMetaElementHtmlParser;
    }

    /**
     * Returns the OpenGraph data of the given url.
     *
     * @param url the url
     * @return the OpenGraph data of the given url
     */
    OpenGraph getOpenGraphOf(String url) {
        final List<OgMetaElement> ogMetaElements = ogMetaElementHtmlParser.getOgMetaElementsFrom(url);

        final Map<String, List<Content>> openGraphMap = convertOgMetaElementsToOpenGraphMap(ogMetaElements);

        return new OpenGraph(openGraphMap);
    }

    private Map<String, List<Content>> convertOgMetaElementsToOpenGraphMap(List<OgMetaElement> ogMetaElements) {
        final Map<String, List<Content>> openGraphMap = new HashMap<>();

        for (OgMetaElement ogMetaElement : ogMetaElements) {
            final String property = ogMetaElement.property();
            final String contentValue = ogMetaElement.content();

            if (openGraphMap.containsKey(property)) {
                updateContents(ogMetaElement, openGraphMap.get(property), contentValue);
                continue;
            }

            openGraphMap.put(property, getNewContents(contentValue));
        }

        return openGraphMap;
    }

    private void updateContents(OgMetaElement ogMetaElement, List<Content> contents, String contentValue) {
        if (ogMetaElement.hasExtraData()) {
            setExtraDataOnLastContent(ogMetaElement, contents, contentValue);
            return;
        }
        contents.add(new Content(contentValue, null));
    }

    private void setExtraDataOnLastContent(OgMetaElement ogMetaElement, List<Content> contents, String contentValue) {
        final Content lastContent = contents.getLast();
        lastContent.addExtraData(ogMetaElement.extraData(), contentValue);
    }

    private List<Content> getNewContents(String content) {
        final List<Content> contents = new ArrayList<>();
        contents.add(new Content(content, null));
        return contents;
    }
}
