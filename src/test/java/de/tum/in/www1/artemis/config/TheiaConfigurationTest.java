package de.tum.in.www1.artemis.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;

class TheiaConfigurationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private TheiaConfiguration theiaConfiguration;

    @Test
    void test() {
        assertThat(theiaConfiguration).isNotNull();
        assert ("test".equals(theiaConfiguration.getImages()));
    }
}
