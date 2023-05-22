package de.tum.in.www1.artemis.service.linkpreview;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.linkpreview.ogparser.Content;
import de.tum.in.www1.artemis.service.linkpreview.ogparser.OgParser;
import de.tum.in.www1.artemis.service.linkpreview.ogparser.OpenGraph;
import de.tum.in.www1.artemis.service.linkpreview.ogparser.htmlparser.OgMetaElementHtmlParser;

/**
 * Service for retrieving meta information from a given url.
 */
@Service
public class LinkPreviewService {

    private final Logger log = LoggerFactory.getLogger(LinkPreviewService.class);

    private OpenGraph openGraph;

    /**
     * Retrieves meta information from a given url.
     *
     * @param url the url to parse
     * @return a String containing the meta information
     */
    public String getLinkPreview(String url) {
        OgParser ogParser = new OgParser(new OgMetaElementHtmlParser());
        openGraph = ogParser.getOpenGraphOf(url);

        Set<String> allProperties = openGraph.getAllProperties();
        for (String allProperty : allProperties) {
            System.out.println(allProperty);
        }
        Content content = openGraph.getContentOf("title");
        System.out.println(content.getValue());
        log.info("Parsing html meta elements from url: {}", url);
        return content.getValue();
    }
}
