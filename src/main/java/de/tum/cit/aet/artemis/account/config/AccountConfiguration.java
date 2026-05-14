package de.tum.cit.aet.artemis.account.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Marker configuration for the Account module.
 *
 * Spring Boot's default component scan from {@code de.tum.cit.aet.artemis} already discovers every
 * {@code @Service}, {@code @Repository}, and {@code @Controller} under
 * {@code de.tum.cit.aet.artemis.account}, so this class is intentionally empty. It exists as the
 * conventional home for future module-level bean definitions and as a single point of attachment
 * for module-wide conditions (see Phase 4 of the module-restructuring plan).
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
public class AccountConfiguration {
}
