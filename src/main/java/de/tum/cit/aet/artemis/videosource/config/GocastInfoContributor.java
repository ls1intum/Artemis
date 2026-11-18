package de.tum.cit.aet.artemis.videosource.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Publishes whether the complete TUM.Live integration configuration is available to the client. */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class GocastInfoContributor implements InfoContributor {

    private final Environment environment;

    public GocastInfoContributor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("gocastEnabled", GocastEnabled.isEnabled(environment));
    }
}
