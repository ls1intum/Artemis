package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * Guards the dependency wiring that OIDCConfiguration relies on.
 * Ensures the essential OAuth2 Client Autoconfiguration class remains on the classpath.
 */
class OIDCAutoConfigurationClasspathTest {

    private static final String AUTOCONFIG_CLASS_NAME = "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration";

    @Test
    void oidcClientAutoConfigurationIsOnClasspath() {
        assertThatCode(() -> Class.forName(AUTOCONFIG_CLASS_NAME))
                .as("OAuth2ClientAutoConfiguration must be on the classpath. Ensure the build declares 'org.springframework.boot:spring-boot-starter-oauth2-client'.")
                .doesNotThrowAnyException();
    }
}
