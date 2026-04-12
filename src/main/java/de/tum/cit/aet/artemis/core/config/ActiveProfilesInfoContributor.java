package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Contributes the list of active Spring profiles to the /management/info endpoint.
 * <p>
 * In Spring Boot 3, active profiles were automatically included via the built-in
 * ActiveProfilesInfoContributor. Spring Boot 4 removed this auto-configuration,
 * so this custom contributor restores the behavior. The Angular client reads
 * {@code activeProfiles} from the info endpoint to determine which features
 * (e.g., LocalCI) are active.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class ActiveProfilesInfoContributor implements InfoContributor {

    private final Environment environment;

    public ActiveProfilesInfoContributor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
    }
}
