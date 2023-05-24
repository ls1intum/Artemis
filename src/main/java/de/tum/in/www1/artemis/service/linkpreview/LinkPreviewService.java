package de.tum.in.www1.artemis.service.linkpreview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.linkpreview.ogparser.Content;
import de.tum.in.www1.artemis.service.linkpreview.ogparser.OgParser;
import de.tum.in.www1.artemis.service.linkpreview.ogparser.OpenGraph;
import de.tum.in.www1.artemis.service.linkpreview.ogparser.htmlparser.OgMetaElementHtmlParser;
import de.tum.in.www1.artemis.web.rest.dto.LinkPreviewDTO;

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
    public LinkPreviewDTO getLinkPreview(String url) {
        log.info("Parsing html meta elements from url: {}", url);

        // Create a new OgParser instance and parse the html meta elements, then get the OpenGraph object
        OgParser ogParser = new OgParser(new OgMetaElementHtmlParser());
        openGraph = ogParser.getOpenGraphOf(url);

        Content titleContent = openGraph.getContentOf("title");
        Content descriptionContent = openGraph.getContentOf("description");
        Content imageContent = openGraph.getContentOf("image");
        Content urlContent = openGraph.getContentOf("url");

        return new LinkPreviewDTO(titleContent.getValue(), descriptionContent.getValue(), imageContent.getValue(), urlContent.getValue());
    }
}
