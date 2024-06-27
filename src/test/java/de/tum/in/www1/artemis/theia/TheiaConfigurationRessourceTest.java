package de.tum.in.www1.artemis.theia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.web.rest.theia.TheiaConfigurationRessource;

public class TheiaConfigurationRessourceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private TheiaConfigurationRessource theiaConfigurationRessource;

    @Test
    void testAutowired() {
        assertThat(theiaConfigurationRessource).isNotNull();
    }

    @Test
    void testFlavorsForLanguage() {
        Map<String, String> images = theiaConfigurationRessource.getImagesForLanguage("java").orElseThrow();
        assertThat(images).hasSize(2);
        assertThat(images).containsKey("Java-17");
        assertThat(images).containsValue("ghcr.io/ls1intum/theia/java-17:latest");
        assertThat(images.get("Java-Non-Existent")).isEqualTo("this-is-not-a-valid-image");
    }

    @Test
    void testNonExistentLanguage() {
        assertThat(theiaConfigurationRessource.getImagesForLanguage("non-existent").isEmpty()).isTrue();
    }

}
