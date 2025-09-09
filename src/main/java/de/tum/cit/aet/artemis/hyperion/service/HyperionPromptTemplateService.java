package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionPromptTemplateService {

    /**
     * Render the template at the given classpath resource path with the provided variables.
     *
     * Supporting placeholders of the form {{var}}
     *
     * @param resourcePath classpath to the template resource
     * @param variables    map of variables used during rendering
     * @return the rendered string
     */
    public String render(String resourcePath, Map<String, String> variables) {
        try {
            var resource = new ClassPathResource(resourcePath);
            String template = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            String rendered = template;
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
