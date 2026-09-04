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
    void render_withNonexistentTemplate_throwsException() {
        Map<String, String> variables = Map.of("name", "John", "score", "95");

        assertThatThrownBy(() -> templateService.render("/nonexistent/template.st", variables)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load prompt template");
    }

    @Test
    void renderObject_withNonexistentTemplate_throwsException() {
        Map<String, Object> variables = Map.of("exerciseId", 123L, "language", "JAVA", "enabled", true);

        assertThatThrownBy(() -> templateService.renderObject("/nonexistent/template.st", variables)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load prompt template");
    }

}
