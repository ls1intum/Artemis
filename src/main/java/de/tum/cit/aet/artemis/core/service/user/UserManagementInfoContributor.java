package de.tum.cit.aet.artemis.core.service.user;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

@Profile(PROFILE_CORE)
@Component
public class UserManagementInfoContributor implements InfoContributor {

    @Value("${artemis.user-management.accept-terms:#{null}}")
    private Optional<Boolean> needsToAcceptTerms;

    @Value("${artemis.user-management.registration.enabled:#{null}}")
    private Optional<Boolean> registrationEnabled;

    @Value("${artemis.user-management.registration.allowed-email-pattern:#{null}}")
    private Optional<Pattern> allowedEmailPattern;

    @Value("${artemis.user-management.registration.allowed-email-pattern-readable:#{null}}")
    private Optional<String> allowedEmailPatternReadable;

    @Value("${artemis.user-management.ldap.allowed-username-pattern:#{null}}")
    private Optional<Pattern> allowedLdapUsernamePattern;

    @Value("${artemis.user-management.course-registration.allowed-username-pattern:#{null}}")
    private Optional<Pattern> allowedCourseRegistrationUsernamePattern;

    @Value("${artemis.user-management.login.account-name:#{null}}")
    private Optional<String> accountName;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.REGISTRATION_ENABLED, registrationEnabled.orElse(Boolean.FALSE));
        builder.withDetail(Constants.NEEDS_TO_ACCEPT_TERMS, needsToAcceptTerms.orElse(Boolean.FALSE));
        allowedEmailPattern.ifPresent(pattern -> builder.withDetail(Constants.ALLOWED_EMAIL_PATTERN, pattern.toString()));
        allowedEmailPatternReadable.ifPresent(patternReadable -> builder.withDetail(Constants.ALLOWED_EMAIL_PATTERN_READABLE, patternReadable));
        allowedLdapUsernamePattern.ifPresent(pattern -> builder.withDetail(Constants.ALLOWED_LDAP_USERNAME_PATTERN, pattern));
        allowedCourseRegistrationUsernamePattern.ifPresent(pattern -> builder.withDetail(Constants.ALLOWED_COURSE_REGISTRATION_USERNAME_PATTERN, pattern));
        accountName.ifPresent(accountName -> builder.withDetail(Constants.ACCOUNT_NAME, accountName));
    }
}
