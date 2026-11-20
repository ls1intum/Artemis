package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * Guards that the production configuration switches off every model type that a Spring AI auto-configuration creates by default.
 * <p>
 * An auto-configuration of Spring AI that is conditional on {@code spring.ai.model.<type>} with {@code matchIfMissing = true} creates the
 * model of the type unless that exact key says {@code none}, and the model needs credentials. A parent key does not do it:
 * {@code spring.ai.model.audio: none} does not switch off {@code spring.ai.model.audio.speech}. Artemis creates all lazy beans after the
 * start-up and ends the process if one fails, so a model type that is not switched off ends a worker, and a server without credentials
 * for it, right after the "Started" banner. Spring AI 2.1 added the decision model, which is how this test came to exist.
 * <p>
 * The keys come from the conditions of the auto-configurations on the classpath, so a model type that a later Spring AI release adds
 * fails this test with its key.
 */
class SpringAiModelTypesConfigurationTest {

    private static final Path MAIN_CONFIG_DIRECTORY = Path.of("src/main/resources/config");

    private static final String MODEL_PREFIX = "spring.ai.model.";

    private static final String AUTO_CONFIGURATION_IMPORTS = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @ParameterizedTest
    @ValueSource(strings = { "application.yml", "application-aiworker.yml" })
    void shouldSwitchOffEveryModelTypeThatIsCreatedByDefault(String configFile) throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader().load(configFile, new FileSystemResource(MAIN_CONFIG_DIRECTORY.resolve(configFile)));

        List<String> typesThatStayOn = modelTypeKeysThatAreOnByDefault().stream().filter(key -> !isSwitchedOff(documents, key)).toList();

        assertThat(typesThatStayOn).as("model types of Spring AI that %s does not switch off, add '<key>: none' for each", configFile).isEmpty();
    }

    /**
     * Without this, the test above would also pass if the scan found no model type.
     */
    @Test
    void shouldFindTheModelTypesOfTheSpringAiAutoConfigurations() throws IOException {
        assertThat(modelTypeKeysThatAreOnByDefault()).contains("spring.ai.model.chat", "spring.ai.model.embedding", "spring.ai.model.image", "spring.ai.model.moderation",
                "spring.ai.model.audio.speech", "spring.ai.model.audio.transcription", "spring.ai.model.decision");
    }

    @Test
    void shouldNotTreatAParentKeyAsTheSwitchOfAModelType() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader().load("parent-key",
                new ByteArrayResource("spring:\n  ai:\n    model:\n      audio: none\n      image: none\n".getBytes(StandardCharsets.UTF_8)));

        assertThat(isSwitchedOff(documents, "spring.ai.model.image")).isTrue();
        assertThat(isSwitchedOff(documents, "spring.ai.model.audio.speech")).as("the condition of the speech model reads its own key").isFalse();
        assertThat(isSwitchedOff(documents, "spring.ai.model.audio.transcription")).isFalse();
    }

    /**
     * The key of every model type that an auto-configuration of Spring AI creates when the key is missing.
     */
    private static Set<String> modelTypeKeysThatAreOnByDefault() throws IOException {
        ClassLoader classLoader = SpringAiModelTypesConfigurationTest.class.getClassLoader();
        MetadataReaderFactory metadataReaders = MetadataReaderFactory.create(classLoader);
        Set<String> keys = new TreeSet<>();
        for (String autoConfiguration : springAiAutoConfigurations(classLoader)) {
            metadataReaders.getMetadataReader(autoConfiguration).getAnnotationMetadata().getAnnotations().stream(ConditionalOnProperty.class)
                    .filter(condition -> condition.getBoolean("matchIfMissing")).forEach(condition -> {
                        String prefix = condition.getString("prefix");
                        String[] names = condition.getStringArray("name").length > 0 ? condition.getStringArray("name") : condition.getStringArray("value");
                        for (String name : names) {
                            String key = prefix.isEmpty() ? name : prefix + (prefix.endsWith(".") ? "" : ".") + name;
                            if (key.startsWith(MODEL_PREFIX)) {
                                keys.add(key);
                            }
                        }
                    });
        }
        return keys;
    }

    private static Set<String> springAiAutoConfigurations(ClassLoader classLoader) throws IOException {
        Set<String> classNames = new TreeSet<>();
        for (URL imports : Collections.list(classLoader.getResources(AUTO_CONFIGURATION_IMPORTS))) {
            try (var lines = new BufferedReader(new InputStreamReader(imports.openStream(), StandardCharsets.UTF_8)).lines()) {
                lines.map(String::strip).filter(line -> line.startsWith("org.springframework.ai.")).forEach(classNames::add);
            }
        }
        return classNames;
    }

    /**
     * A model type is switched off by its own key. A key above it is not enough.
     */
    private static boolean isSwitchedOff(List<PropertySource<?>> documents, String key) {
        return documents.stream().filter(EnumerablePropertySource.class::isInstance).map(document -> document.getProperty(key)).filter(Objects::nonNull)
                .anyMatch(value -> "none".equals(value.toString()));
    }
}
