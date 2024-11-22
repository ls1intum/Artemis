package de.tum.cit.aet.artemis.communication.service.notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URL;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * This service implements the rendering of markdown tags that represent a link.
 * It takes the tag, transforms it into an <a></a> tag, and sets the corresponding href.
 */
@Profile(PROFILE_CORE)
@Service
public class MarkdownCustomLinkRendererService implements MarkdownCustomRendererService {

    private static final Logger log = LoggerFactory.getLogger(MarkdownCustomLinkRendererService.class);

    private final Set<String> supportedTags;

    @Value("${server.url}")
    private URL artemisServerUrl;

    public MarkdownCustomLinkRendererService() {
        this.supportedTags = Set.of("programming", "modeling", "quiz", "text", "file-upload", "lecture", "attachment", "lecture-unit", "slide", "faq");
    }

    /**
     * Takes a string and replaces all occurrences of custom markdown tags (e.g. [programming], [faq], etc.) with a link
     *
     * @param content string to render
     *
     * @return the newly rendered string.
     */
    public String render(String content) {
        String tagPattern = String.join("|", supportedTags);
        // The pattern checks for the occurrence of any tag and then extracts the link from it
        Pattern pattern = Pattern.compile("\\[(" + tagPattern + ")\\](.*?)\\((.*?)\\)(.*?)\\[/\\1\\]");
        Matcher matcher = pattern.matcher(content);
        String parsedContent = content;

        while (matcher.find()) {
            try {
                String textStart = matcher.group(2);
                String link = matcher.group(3);
                String textEnd = matcher.group(4);
                String text = (textStart + " " + textEnd).trim();

                String absoluteUrl = UriComponentsBuilder.fromUri(artemisServerUrl.toURI()).path(link).build().toUriString();

                parsedContent = parsedContent.substring(0, matcher.start()) + "<a href=\"" + absoluteUrl + "\">" + text + "</a>" + parsedContent.substring(matcher.end());
            }
            catch (Exception e) {
                log.error("Not able to render tag. Replacing with empty.", e);
                parsedContent = parsedContent.substring(0, matcher.start()) + parsedContent.substring(matcher.end());
            }

            matcher = pattern.matcher(parsedContent);
        }

        return parsedContent;
    }
}
