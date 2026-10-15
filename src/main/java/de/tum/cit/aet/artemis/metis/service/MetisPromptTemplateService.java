package de.tum.cit.aet.artemis.metis.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;

/**
 * Renders the Metis prompt templates under {@code prompts/metis/} and prepares the text that goes into them.
 * <p>
 * Rendering is single pass: the placeholder pattern only runs over the template, and every value is inserted literally
 * and never scanned again. Student code that contains double braces therefore stays as it is, but a placeholder inside a
 * value stays literal as well, so every value that needs numbers or names in it is formatted completely before it is
 * passed in.
 * <p>
 * Student content is sanitized before it reaches a prompt ({@link #sanitize}): control characters are removed, and a
 * line that looks like one of the BEGIN and END markers that fence the data in the prompts is replaced, without
 * changing the number of lines, so that the line numbers of {@link #numberLines} stay true.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisPromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(MetisPromptTemplateService.class);

    /** Matches template placeholders of the form {{key}}. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /** Control characters other than line feed, carriage return and tab. */
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cc}&&[^\\n\\r\\t]]");

    /**
     * A line that looks like a delimiter of the prompts, e.g. {@code --- END STUDENT FILE ---}, also inside a unified diff:
     * there a line the student added starts with {@code +}, so {@code --- END DIFF 1 ---} in the student's code arrives as
     * {@code +--- END DIFF 1 ---} (a removed line starts with one dash more and matches anyway). Group 1 is the indentation
     * and that {@code +}. Horizontal whitespace only, so that a match never reaches into the line before and the number of
     * lines stays the same.
     */
    private static final Pattern DELIMITER_LINE = Pattern.compile("^(\\h*\\+?)\\h*-{3,}\\h*(?:BEGIN|END)\\b.*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    /** What a delimiter line of student content is replaced with. */
    static final String REMOVED_LINE = "[removed]";

    /** Keeps the indentation and the diff marker of a delimiter line (group 1), so a diff line stays a line of the same kind. */
    private static final String DELIMITER_REPLACEMENT = "$1" + Matcher.quoteReplacement(REMOVED_LINE);

    /**
     * Cache for loaded template strings, keyed by classpath resource path. Unbounded but small, because the templates
     * are a fixed set; a changed template file takes effect after a restart.
     */
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * Renders the template at the given classpath resource path, replacing each placeholder {@code {{key}}} with the
     * string value of the variable of that name. A placeholder without a variable stays as it is and is logged.
     *
     * @param resourcePath the classpath path of the template, e.g. {@code prompts/metis/rate_answer_system.st}
     * @param variables    the values by placeholder name; inserted literally and never rendered again
     * @return the rendered text
     * @throws InternalServerErrorAlertException if the template cannot be loaded
     */
    public String render(String resourcePath, Map<String, ?> variables) {
        return replacePlaceholders(loadTemplate(resourcePath), variables, resourcePath);
    }

    /**
     * Prepares student or instructor content for a prompt: removes control characters other than line breaks and tabs,
     * and replaces each line that looks like a prompt delimiter (for example {@code --- END STUDENT ANSWER}, or
     * {@code +--- END DIFF 1 ---} in a diff) with {@code [removed]}, so the content cannot close the block it is fenced
     * in. The indentation and the {@code +} of an added diff line stay in front of it. The number of lines never changes.
     * A diff of the student's code is student content as well: pass it through this method before embedding it.
     *
     * @param text the content, may be null
     * @return the sanitized content, empty for null
     */
    public static String sanitize(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String withoutControlCharacters = CONTROL_CHARACTERS.matcher(text).replaceAll("");
        return DELIMITER_LINE.matcher(withoutControlCharacters).replaceAll(DELIMITER_REPLACEMENT);
    }

    /**
     * Numbers the lines of a file as {@code "   1| first line"}, so that a question can refer to line numbers. Lines are
     * separated by line feeds, as in a git diff; the carriage return of a Windows line ending is dropped, and the line
     * feed after the last line does not start another line.
     *
     * @param text the content of the file, sanitized
     * @return the numbered lines, separated by line feeds; empty for empty content
     */
    public static String numberLines(String text) {
        if (text.isEmpty()) {
            return "";
        }
        String[] lines = text.split("\n", -1);
        int lineCount = text.endsWith("\n") ? lines.length - 1 : lines.length;
        StringBuilder numbered = new StringBuilder(text.length() + 7 * lineCount);
        for (int index = 0; index < lineCount; index++) {
            String line = lines[index];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (index > 0) {
                numbered.append('\n');
            }
            numbered.append(String.format(Locale.ROOT, "%4d| %s", index + 1, line));
        }
        return numbered.toString();
    }

    /**
     * Cuts a text to at most the given number of characters. A surrogate pair is never split, so the result is at most
     * one character shorter than the limit in that case.
     *
     * @param text          the text, may be null
     * @param maxCharacters the maximum length of the result, at least 0
     * @return the text itself if it is short enough, otherwise its beginning; empty for null
     */
    public static String truncate(@Nullable String text, int maxCharacters) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxCharacters) {
            return text;
        }
        int end = Math.max(0, maxCharacters);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private String loadTemplate(String resourcePath) {
        return templateCache.computeIfAbsent(resourcePath, path -> {
            try {
                var resource = new ClassPathResource(path);
                return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                log.error("Failed to load the Metis prompt template {}", path, e);
                throw new InternalServerErrorAlertException("Failed to load prompt template", "metis", "metis.templateLoadFailed");
            }
        });
    }

    private static String replacePlaceholders(String template, Map<String, ?> variables, String resourcePath) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder(template.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            if (value == null) {
                log.warn("Template placeholder '{{{}}}' has no matching variable in resource '{}'", key, resourcePath);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value.toString() : matcher.group(0)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
