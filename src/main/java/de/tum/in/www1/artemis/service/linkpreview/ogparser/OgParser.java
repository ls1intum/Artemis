package de.tum.in.www1.artemis.service.linkpreview.ogparser;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.linkpreview.ogparser.htmlparser.OgMetaElement;
import de.tum.in.www1.artemis.service.linkpreview.ogparser.htmlparser.OgMetaElementHtmlParser;

/**
 * Parses the Open Graph meta tags of a website.
 */
@Profile(PROFILE_CORE)
@Component
public class OgParser {

    private final OgMetaElementHtmlParser ogMetaElementHtmlParser;

    public OgParser() {
        this.ogMetaElementHtmlParser = new OgMetaElementHtmlParser();
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
