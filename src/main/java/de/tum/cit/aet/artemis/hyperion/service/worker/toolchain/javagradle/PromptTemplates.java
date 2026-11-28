package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle;

import java.util.Map;

import de.tum.cit.aet.artemis.hyperion.runtime.template.PromptTemplateRenderer;

public class PromptTemplates {

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer(cause -> new IllegalStateException("Failed to load prompt template", cause));

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
