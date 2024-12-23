package de.tum.cit.aet.artemis.communication.service.notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * This service implements the rendering of markdown tags that represent a reference (to e.g. a user).
 * These references cannot directly represent a link, so they are rendered as their text only.
 */
@Profile(PROFILE_CORE)
@Service
public class MarkdownCustomReferenceRendererService implements MarkdownCustomRendererService {

    private static final Logger log = LoggerFactory.getLogger(MarkdownCustomReferenceRendererService.class);

    private final Set<String> supportedTags;

    private final HashMap<String, String> startingCharacters;

    public MarkdownCustomReferenceRendererService() {
        supportedTags = Set.of("user", "channel");
        startingCharacters = new HashMap<>();
        startingCharacters.put("user", "@");
        startingCharacters.put("channel", "#");
    }

    /**
     * Takes a string and replaces all occurrences of custom markdown tags (e.g. [user], [channel], etc.) with text.
     * To make it better readable, it prepends an appropriate character. (e.g. for users an @, for channels a #)
     *
     * @param content string to render
     *
     * @return the newly rendered string.
     */
    @Override
    public String render(String content) {
        String tagPattern = String.join("|", supportedTags);
        Pattern pattern = Pattern.compile("\\[(" + tagPattern + ")\\](.*?)\\((.*?)\\)(.*?)\\[/\\1\\]");
        Matcher matcher = pattern.matcher(content);
        String parsedContent = content;

        while (matcher.find()) {
            try {
                String tag = matcher.group(1);
                String startingCharacter = startingCharacters.get(tag);
                startingCharacter = startingCharacter == null ? "" : startingCharacter;
                String textStart = matcher.group(2);
                String textEnd = matcher.group(4);
                String text = startingCharacter + (textStart + " " + textEnd).trim();

                parsedContent = parsedContent.substring(0, matcher.start()) + text + parsedContent.substring(matcher.end());
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
