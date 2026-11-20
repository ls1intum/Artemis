package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * Guards that the production configuration switches off every model type that Spring AI offers.
 * <p>
 * Spring AI creates the model of a type unless {@code spring.ai.model.<type>} says {@code none}, and the model needs credentials. Artemis
 * creates all lazy beans after the start-up and ends the process if one of them fails, so a model type that is not switched off ends a
 * server without credentials for it, and a worker, right after the "Started" banner. Spring AI 2.1 added the decision model, which is how
 * this test came to exist. A later Spring AI release may add another type: this test then fails with its key.
 */
class SpringAiModelTypesConfigurationTest {

    private static final Path MAIN_CONFIG_DIRECTORY = Path.of("src/main/resources/config");

    private static final String MODEL_PREFIX = "spring.ai.model";

    @ParameterizedTest
    @ValueSource(strings = { "application.yml", "application-aiworker.yml" })
    void shouldSwitchOffEveryModelTypeOfSpringAi(String configFile) throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader().load(configFile, new FileSystemResource(MAIN_CONFIG_DIRECTORY.resolve(configFile)));

        List<String> typesThatStayOn = modelTypeKeys().stream().filter(key -> !isSwitchedOff(documents, key)).toList();

        assertThat(typesThatStayOn).as("model types of Spring AI that %s does not switch off, add '<key>: none' for each", configFile).isEmpty();
    }

    /**
     * The key of every model type that Spring AI knows, for example {@code spring.ai.model.chat}.
     */
    private static List<String> modelTypeKeys() {
        List<String> keys = new ArrayList<>();
        for (Field field : SpringAIModelProperties.class.getDeclaredFields()) {
            boolean isModelTypeKey = Modifier.isStatic(field.getModifiers()) && field.getType() == String.class && field.getName().endsWith("_MODEL");
            if (isModelTypeKey) {
                try {
                    keys.add((String) field.get(null));
                }
                catch (IllegalAccessException ex) {
                    throw new IllegalStateException("Cannot read " + field.getName(), ex);
                }
            }
        }
        assertThat(keys).as("the model type keys that were found").isNotEmpty().allMatch(key -> key.startsWith(MODEL_PREFIX + "."));
        return keys;
    }

    /**
     * A type is switched off by its own key or by a key above it, as {@code spring.ai.model.embedding: none} also covers
     * {@code spring.ai.model.embedding.text}.
     */
    private static boolean isSwitchedOff(List<PropertySource<?>> documents, String key) {
        String candidate = key;
        while (candidate.length() > MODEL_PREFIX.length()) {
            String candidateKey = candidate;
            boolean none = documents.stream().filter(EnumerablePropertySource.class::isInstance).map(document -> document.getProperty(candidateKey)).filter(Objects::nonNull)
                    .anyMatch(value -> "none".equals(value.toString()));
            if (none) {
                return true;
            }
            candidate = candidate.substring(0, candidate.lastIndexOf('.'));
        }
        return false;
    }
}
