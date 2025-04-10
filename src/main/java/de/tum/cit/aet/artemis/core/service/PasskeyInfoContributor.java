package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

@Profile(PROFILE_CORE)
@Component
public class PasskeyInfoContributor implements InfoContributor {

    private final boolean enabled;

    public PasskeyInfoContributor(@Value("${" + Constants.PASSKEY_ENABLED_PROPERTY_NAME + ":false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.PASSKEY_ENABLED, enabled);
    }
}
