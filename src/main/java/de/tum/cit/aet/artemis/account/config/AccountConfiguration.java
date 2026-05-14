package de.tum.cit.aet.artemis.account.config;

import org.springframework.context.annotation.Configuration;

/**
 * Marker configuration for the Account module.
 *
 * Spring Boot's default component scan from {@code de.tum.cit.aet.artemis} already discovers every
 * {@code @Service}, {@code @Repository}, and {@code @Controller} under
 * {@code de.tum.cit.aet.artemis.account}, so this class is intentionally empty. It exists as the
 * conventional home for future module-level bean definitions and as a single point of attachment
 * for module-wide conditions (see Phase 4 of the module-restructuring plan).
 */
@Configuration
public class AccountConfiguration {
}
