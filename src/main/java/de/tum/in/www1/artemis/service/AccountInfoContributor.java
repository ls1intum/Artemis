package de.tum.in.www1.artemis.service;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
public class AccountInfoContributor implements InfoContributor {

    @Value("${artemis.user-management.registration.enabled:#{null}}")
    private Optional<Boolean> registrationEnabled;

    @Value("${artemis.user-management.registration.allowed-email-pattern:#{null}}")
    private Optional<Pattern> allowedEmailPattern;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.REGISTRATION_ENABLED, registrationEnabled.orElse(Boolean.FALSE));
        allowedEmailPattern.ifPresent(pattern -> builder.withDetail(Constants.ALLOWED_EMAIL_PATTERN, pattern.toString()));
    }
}
