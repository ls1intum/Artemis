package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HyperionPromptTemplateServiceTest {

    private HyperionPromptTemplateService templateService;

    @BeforeEach
    void setup() {
        templateService = new HyperionPromptTemplateService();
    }

    @Test
    void render_withValidTemplateAndVariables_replacesPlaceholders() throws Exception {
        String templateContent = "Hello {{name}}, your score is {{score}}!";

        Map<String, String> variables = Map.of("name", "John", "score", "95");

        Map<String, Object> objectVariables = Map.of("name", "John", "score", 95);

        assertThatThrownBy(() -> templateService.renderObject("/nonexistent/template.st", objectVariables)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load template");
    }

    @Test
    void render_withStringVariables_replacesCorrectly() {
        String template = "Programming language: {{language}}, Problem: {{problem}}";
        Map<String, String> variables = Map.of("language", "Java", "problem", "Implement sorting");

        assertThatThrownBy(() -> templateService.render("/nonexistent/template.st", variables)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load template");
    }

    @Test
    void renderObject_withObjectVariables_handlesConversion() {
        Map<String, Object> variables = Map.of("exerciseId", 123L, "language", "JAVA", "enabled", true);

        assertThatThrownBy(() -> templateService.renderObject("/nonexistent/template.st", variables)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load template");
    }

    @Test
    void render_withNullResourcePath_throwsException() {
        Map<String, String> variables = Map.of("key", "value");

        assertThatThrownBy(() -> templateService.render(null, variables)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void renderObject_withNullResourcePath_throwsException() {
        Map<String, Object> variables = Map.of("key", "value");

        assertThatThrownBy(() -> templateService.renderObject(null, variables)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void render_withEmptyVariables_processesTemplate() {
        Map<String, String> variables = Map.of();

        assertThatThrownBy(() -> templateService.render("/nonexistent/template.st", variables)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load template");
    }
}
