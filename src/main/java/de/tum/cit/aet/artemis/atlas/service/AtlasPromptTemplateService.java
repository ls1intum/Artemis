package de.tum.cit.aet.artemis.atlas.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * Service for rendering prompt templates for Atlas AI functionality.
 * Loads templates from classpath resources and supports variable substitution.
 */
@Service
@Lazy
@Conditional(AtlasEnabled.class)
public class AtlasPromptTemplateService {

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
        try {
            var resource = new ClassPathResource(resourcePath);
            String rendered;
            try (var is = resource.getInputStream()) {
                rendered = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }
            for (var entry : variables.entrySet()) {
                rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return rendered;
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to load template: " + resourcePath, e);
        }
    }
}
