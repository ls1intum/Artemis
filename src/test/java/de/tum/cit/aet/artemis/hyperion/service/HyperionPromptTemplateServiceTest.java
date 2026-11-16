package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.PromptTemplates;

class HyperionPromptTemplateServiceTest {

    private static final String TEMPLATE = "templates/hyperion/prompt-template.txt";

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void rendersRepeatedPlaceholdersAndConvertsObjects(boolean worker) {
        String result = render(worker, TEMPLATE, Map.of("greeting", "Hello", "name", "Alice", "score", 42, "missing", false));

        assertThat(result).isEqualTo("Hello Alice! Score: 42. Again: Alice. Missing: false.\n");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void preservesMissingAndNullPlaceholders(boolean worker) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("greeting", "Hello");
        variables.put("name", null);

        assertThat(render(worker, TEMPLATE, variables)).isEqualTo("Hello {{name}}! Score: {{score}}. Again: {{name}}. Missing: {{missing}}.\n");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void insertsValuesLiterallyWithoutRenderingThemAgain(boolean worker) {
        String result = render(worker, TEMPLATE, Map.of("greeting", "$1\\path", "name", "{{score}}", "score", 42));

        assertThat(result).isEqualTo("$1\\path {{score}}! Score: 42. Again: {{score}}. Missing: {{missing}}.\n");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void reusesCachedTemplateWithFreshVariables(boolean worker) {
        var service = new HyperionPromptTemplateService();
        var templates = new PromptTemplates();
        Map<String, String> first = Map.of("name", "Alice");
        Map<String, String> second = Map.of("name", "Bob");

        String firstResult = worker ? templates.render(TEMPLATE, first) : service.render(TEMPLATE, first);
        String secondResult = worker ? templates.render(TEMPLATE, second) : service.render(TEMPLATE, second);

        assertThat(firstResult).contains("Alice").doesNotContain("Bob");
        assertThat(secondResult).contains("Bob").doesNotContain("Alice");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void keepsEachCallersLoadFailureType(boolean worker) {
        Class<? extends RuntimeException> expected = worker ? IllegalStateException.class : InternalServerErrorAlertException.class;

        assertThatThrownBy(() -> render(worker, "nonexistent/template.txt", Map.of())).isInstanceOf(expected).hasMessageContaining("Failed to load prompt template");
    }

    private String render(boolean worker, String resourcePath, Map<String, ?> variables) {
        return worker ? new PromptTemplates().renderObject(resourcePath, variables) : new HyperionPromptTemplateService().renderObject(resourcePath, variables);
    }
}
