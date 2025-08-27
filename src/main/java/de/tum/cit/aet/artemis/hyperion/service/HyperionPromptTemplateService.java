package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionPromptTemplateService {

    // Internally render with Unicode angle quote delimiters « » (robust for JSON in prompts).
    // To keep authoring simple, we also accept Mustache-style placeholders {{var}} and convert them.
    private final StTemplateRenderer renderer = StTemplateRenderer.builder().startDelimiterToken('«').endDelimiterToken('»').build();

    /**
     * Render the template at the given classpath resource path with the provided variables.
     *
     * @param resourcePath classpath to the template resource
     * @param variables    map of variables used during rendering
     * @return the rendered string
     */
    public String render(String resourcePath, Map<String, Object> variables) {
        try {
            var resource = new ClassPathResource(resourcePath);
            String template = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            // Support {{var}} as authoring delimiters by translating to «var» for ST v4.
            // Only replace the delimiter tokens to avoid touching JSON braces in the prompt content.
            if (template.contains("{{") && template.contains("}}")) {
                template = template.replace("{{", "«").replace("}}", "»");
            }
            return renderer.apply(template, Map.copyOf(variables));
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to load template: " + resourcePath, e);
        }
    }
}
