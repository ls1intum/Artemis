package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;

/**
 * Guards the dependency wiring that {@link SAML2Configuration} relies on.
 * <p>
 * Spring Boot 4 modularized its autoconfigurations: the SAML2 relying-party autoconfig was
 * extracted from {@code spring-boot-autoconfigure} into the dedicated
 * {@code spring-boot-starter-security-saml2} module, moving to a new package
 * ({@code org.springframework.boot.security.saml2.autoconfigure}). Without that module on the
 * classpath the {@code spring.security.saml2.relyingparty.*} properties are never bound and no
 * {@link RelyingPartyRegistrationRepository} bean is produced, causing startup to fail with the
 * {@code saml2} profile active. See issue #12563.
 */
class SAML2AutoConfigurationClasspathTest {

    private static final String AUTOCONFIG_CLASS_NAME = "org.springframework.boot.security.saml2.autoconfigure.Saml2RelyingPartyAutoConfiguration";

    @Test
    void saml2RelyingPartyAutoConfigurationIsOnClasspath() {
        assertThatCode(() -> Class.forName(AUTOCONFIG_CLASS_NAME))
                .as("Saml2RelyingPartyAutoConfiguration must be on the classpath. Ensure the build declares 'org.springframework.boot:spring-boot-starter-security-saml2'.")
                .doesNotThrowAnyException();
    }
}
