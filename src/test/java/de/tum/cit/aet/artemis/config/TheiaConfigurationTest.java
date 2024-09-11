package de.tum.cit.aet.artemis.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;

class TheiaConfigurationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private TheiaConfiguration theiaConfiguration;

    @Test
    void testAutowired() {
        assertThat(theiaConfiguration).isNotNull();
    }

    @Test
    void testAmountOfLanguageImages() {
        assertThat(theiaConfiguration.getImagesForAllLanguages()).hasSize(2);
    }

    @Test
    void testFlavorsForLanguage() {
        Map<String, String> images = theiaConfiguration.getImagesForLanguage(ProgrammingLanguage.valueOf("JAVA"));
        assertThat(images).hasSize(2);
        assertThat(images).containsKey("Java-17");
        assertThat(images).containsValue("ghcr.io/ls1intum/theia/java-17:latest");
        assertThat(images.get("Java-Non-Existent")).isEqualTo("this-is-not-a-valid-image");
    }

    @Test
    void testNonExistentLanguage() {
        assertThat(theiaConfiguration.getImagesForLanguage(null)).isNull();
    }
}
