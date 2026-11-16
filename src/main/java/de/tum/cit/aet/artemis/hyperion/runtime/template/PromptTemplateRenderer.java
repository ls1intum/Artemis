package de.tum.cit.aet.artemis.hyperion.runtime.template;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/** Cached classpath prompt rendering shared by the authoring service and worker. */
public final class PromptTemplateRenderer {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateRenderer.class);

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

    private final Function<IOException, RuntimeException> loadFailure;

    /**
     * @param loadFailure creates the caller-specific exception when a classpath template cannot be loaded
     */
    public PromptTemplateRenderer(Function<IOException, RuntimeException> loadFailure) {
        this.loadFailure = loadFailure;
    }

    /**
     * Renders placeholders once, converting values to strings and preserving missing variables.
     *
     * @param resourcePath classpath location of the template
     * @param variables    placeholder values
     * @return the rendered template
     */
    public String render(String resourcePath, Map<String, ?> variables) {
        return replacePlaceholders(loadTemplate(resourcePath), variables, resourcePath);
    }

    private String loadTemplate(String resourcePath) {
        return templateCache.computeIfAbsent(resourcePath, path -> {
            try (var input = new ClassPathResource(path).getInputStream()) {
                return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                log.error("Failed to load prompt template at classpath location", e);
                throw loadFailure.apply(e);
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
