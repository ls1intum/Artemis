package de.tum.in.www1.artemis.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import static org.junit.Assert.assertNotNull;

@EnableConfigurationProperties(TheiaConfiguration.class)
class TheiaConfigurationTest {

    @Autowired
    private TheiaConfiguration theiaConfiguration;

    @Test
    void test() {
        assertNotNull(theiaConfiguration);
        assert("test".equals(theiaConfiguration.getImages()));
    }
}
