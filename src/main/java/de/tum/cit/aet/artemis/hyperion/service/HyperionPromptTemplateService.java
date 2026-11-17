package de.tum.cit.aet.artemis.hyperion.service;

import java.util.Map;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.runtime.template.PromptTemplateRenderer;

@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionPromptTemplateService {

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer(
            cause -> new InternalServerErrorAlertException("Failed to load prompt template", "Hyperion", "hyperion.templateLoadFailed"));

    /**
     * @param resourcePath classpath template location
     * @param variables    placeholder values
     * @return the rendered template
     */
    public String render(String resourcePath, Map<String, String> variables) {
        return renderObject(resourcePath, variables);
    }

    /**
     * @param resourcePath classpath template location
     * @param variables    placeholder values, converted using {@link Object#toString()}
     * @return the rendered template
     */
    public String renderObject(String resourcePath, Map<String, ?> variables) {
        return renderer.render(resourcePath, variables);
    }
}
