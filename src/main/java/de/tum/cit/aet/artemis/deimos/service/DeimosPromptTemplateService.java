package de.tum.cit.aet.artemis.deimos.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class DeimosPromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(DeimosPromptTemplateService.class);

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

    public String render(String resourcePath, Map<String, String> variables) {
        String template = loadTemplate(resourcePath);
        return replacePlaceholders(template, variables, resourcePath);
    }

    private String loadTemplate(String resourcePath) {
        return templateCache.computeIfAbsent(resourcePath, path -> {
            try {
                var resource = new ClassPathResource(path);
                return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                log.error("Failed to load Deimos prompt template {}", path, e);
                throw new InternalServerErrorAlertException("Failed to load prompt template", "Deimos", "deimos.templateLoadFailed");
            }
        });
    }

    private static String replacePlaceholders(String template, Map<String, String> variables, String resourcePath) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                log.warn("Template placeholder '{{{}}}' has no matching variable in resource '{}'", key, resourcePath);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value : matcher.group(0)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
