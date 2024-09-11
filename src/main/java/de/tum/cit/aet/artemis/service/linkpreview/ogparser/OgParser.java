package de.tum.cit.aet.artemis.service.linkpreview.ogparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.service.linkpreview.ogparser.htmlparser.OgMetaElement;
import de.tum.cit.aet.artemis.service.linkpreview.ogparser.htmlparser.OgMetaElementHtmlParser;

/**
 * Parses the Open Graph meta tags of a website.
 */
public class OgParser {

    private final OgMetaElementHtmlParser ogMetaElementHtmlParser;

    public OgParser() {
        this.ogMetaElementHtmlParser = new OgMetaElementHtmlParser();
    }

    public OgParser(OgMetaElementHtmlParser ogMetaElementHtmlParser) {
        this.ogMetaElementHtmlParser = ogMetaElementHtmlParser;
    }

    /**
     * Returns the OpenGraph data of the given url.
     *
     * @param url the url
     * @return the OpenGraph data of the given url
     */
    public OpenGraph getOpenGraphOf(String url) {
        final List<OgMetaElement> ogMetaElements = ogMetaElementHtmlParser.getOgMetaElementsFrom(url);

        final Map<String, List<Content>> openGraphMap = convertOgMetaElementsToOpenGraphMap(ogMetaElements);

        return new OpenGraph(openGraphMap);
    }

    private Map<String, List<Content>> convertOgMetaElementsToOpenGraphMap(List<OgMetaElement> ogMetaElements) {
        final Map<String, List<Content>> openGraphMap = new HashMap<>();

        for (OgMetaElement ogMetaElement : ogMetaElements) {
            final String property = ogMetaElement.getProperty();
            final String contentValue = ogMetaElement.getContent();

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
        contents.add(new Content(contentValue));
    }

    private void setExtraDataOnLastContent(OgMetaElement ogMetaElement, List<Content> contents, String contentValue) {
        final Content lastContent = contents.getLast();
        lastContent.setExtraData(ogMetaElement.getExtraData(), contentValue);
    }

    private List<Content> getNewContents(String content) {
        final List<Content> contents = new ArrayList<>();
        contents.add(new Content(content));
        return contents;
    }
}
