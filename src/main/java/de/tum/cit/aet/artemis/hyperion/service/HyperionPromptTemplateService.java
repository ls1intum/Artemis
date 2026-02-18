package de.tum.cit.aet.artemis.hyperion.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionPromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(HyperionPromptTemplateService.class);

    /** Matches template placeholders of the form {{key}}. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /** Cache for loaded template strings, keyed by classpath resource path. */
    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

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
        return renderObject(resourcePath, Map.copyOf(variables));
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
    public String renderObject(String resourcePath, Map<String, Object> variables) {
        String template = loadTemplate(resourcePath);
        return replacePlaceholders(template, variables);
    }

    private String loadTemplate(String resourcePath) {
        return templateCache.computeIfAbsent(resourcePath, path -> {
            try {
                var resource = new ClassPathResource(path);
                return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                log.error("Failed to load prompt template at classpath location", e);
                throw new InternalServerErrorAlertException("Failed to load prompt template", "Hyperion", "hyperion.templateLoadFailed");
            }
        });
    }

    private static String replacePlaceholders(String template, Map<String, Object> variables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value.toString() : matcher.group(0)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
