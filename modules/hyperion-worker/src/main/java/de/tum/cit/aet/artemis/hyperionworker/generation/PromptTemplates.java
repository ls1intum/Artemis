package de.tum.cit.aet.artemis.hyperionworker.generation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

public class PromptTemplates {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplates.class);

    /** Matches template placeholders of the form {{key}}. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * Cache for loaded template strings, keyed by classpath resource path.
     * <p>
     * This cache is unbounded but safe in practice because the number of prompt templates
     * is small and fixed at compile time. Note: changes to template files on the classpath
     * will not take effect until the server is restarted.
     */
    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * Clears the template cache. Intended for testing only.
     */
    void clearCache() {
        templateCache.clear();
    }

    /**
     * Render the template at the given classpath resource path with the provided variables.
     * <p>
     * Supporting placeholders of the form {{var}}
     *
     * @param resourcePath classpath to the template resource
     * @param variables    map of variables used during rendering
     * @return the rendered string
     */
    public String render(String resourcePath, Map<String, String> variables) {
        return renderObject(resourcePath, variables);
    }

    /**
     * Render the template at the given classpath resource path with the provided variables.
     * <p>
     * Supporting placeholders of the form {{var}}. Values are converted to strings via {@link Object#toString()}.
     *
     * @param resourcePath classpath to the template resource
     * @param variables    map of variables used during rendering
     * @return the rendered string
     */
    public String renderObject(String resourcePath, Map<String, ?> variables) {
        String template = loadTemplate(resourcePath);
        return replacePlaceholders(template, variables, resourcePath);
    }

    private String loadTemplate(String resourcePath) {
        return templateCache.computeIfAbsent(resourcePath, path -> {
            try (var input = new ClassPathResource(path).getInputStream()) {
                return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                log.error("Failed to load prompt template at classpath location", e);
                throw new IllegalStateException("Failed to load prompt template", e);
            }
        });
    }

    private static String replacePlaceholders(String template, Map<String, ?> variables, String resourcePath) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
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
