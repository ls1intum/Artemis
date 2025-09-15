package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
@Profile(PROFILE_HYPERION)
public class PromptTemplateService {

    /**
     * Renders a template from the given resource path with the provided variables.
     *
     * @param resourcePath the path to the template resource
     * @param variables    the variables to substitute in the template
     * @return the rendered template as a string
     */
    public String render(String resourcePath, Map<String, Object> variables) {
        try {
            var resource = new ClassPathResource(resourcePath);
            String template = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            PromptTemplate promptTemplate = new PromptTemplate(template);
            return promptTemplate.render(variables);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to load template: " + resourcePath, e);
        }
    }
}
