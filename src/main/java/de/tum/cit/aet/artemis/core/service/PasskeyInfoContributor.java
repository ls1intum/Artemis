package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

@Profile(PROFILE_CORE)
@Component
@EnableConfigurationProperties
@ConfigurationProperties("artemis.user-management.passkey")
public class PasskeyInfoContributor implements InfoContributor {

    @Value("${artemis.user-management.passkey.enabled:false}")
    public boolean enabled;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.PASSKEY_ENABLED, enabled);
    }
}
