package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;

/**
 * Guards the entry of {@code META-INF/spring.factories} that Spring resolves by interface name.
 * <p>
 * A key that no longer matches the interface fails silently: the class is never loaded, and the application starts and
 * behaves normally until whatever it configured is needed. {@link EnvironmentPostProcessor} moved from
 * {@code org.springframework.boot.env} to {@code org.springframework.boot} in Spring Boot 4.1, which is exactly the
 * kind of change that leaves such an entry pointing at nothing. Asserting against the compiled interface name rather
 * than a literal means the next move is caught here instead of in production.
 */
class SpringFactoriesRegistrationTest {

    @Test
    void shouldRegisterTheRedisDiscoveryEnvironmentPostProcessorUnderTheInterfaceSpringReads() throws IOException {
        String registeredClass = RedisDiscoveryEnvironmentPostProcessor.class.getName();
        List<String> keysDeclaringIt = new ArrayList<>();

        for (URL resource : Collections.list(getClass().getClassLoader().getResources("META-INF/spring.factories"))) {
            Properties factories = new Properties();
            try (InputStream stream = resource.openStream()) {
                factories.load(stream);
            }
            factories.forEach((key, value) -> {
                if (value.toString().contains(registeredClass)) {
                    keysDeclaringIt.add(key.toString());
                }
            });
        }

        assertThat(keysDeclaringIt).as("%s must be declared exactly once, under the interface Spring Boot actually reads", registeredClass)
                .containsExactly(EnvironmentPostProcessor.class.getName());
    }
}
