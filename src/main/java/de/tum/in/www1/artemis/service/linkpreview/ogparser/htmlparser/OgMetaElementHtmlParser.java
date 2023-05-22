package de.tum.in.www1.artemis.service.linkpreview.ogparser.htmlparser;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses html meta elements from a given url.
 */
public class OgMetaElementHtmlParser {

    private final Logger log = LoggerFactory.getLogger(OgMetaElementHtmlParser.class);

    /**
     * Parses html meta elements from a given url.
     *
     * @param url the url to parse
     * @return a list of OgMetaElements
     */
    public List<OgMetaElement> getOgMetaElementsFrom(String url) {
        try {
            log.info("Parsing html meta elements from url: {}", url);
            final Document document = Jsoup.connect(url).get();
            final Elements metaElements = document.select("meta");
            return metaElements.stream().filter(m -> m.attr("property").startsWith("og:")).map(m -> {
                final String property = m.attr("property").substring(3).trim();
                final String content = m.attr("content");
                return new OgMetaElement(property, content);
            }).collect(Collectors.toList());
        }
        catch (IOException e) {
            log.info("IOException occurred while parsing html meta elements.");
            return Collections.emptyList();
        }
    }
}
