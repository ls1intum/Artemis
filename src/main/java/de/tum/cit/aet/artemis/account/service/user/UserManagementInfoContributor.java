package de.tum.cit.aet.artemis.account.service.user;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

@Profile(PROFILE_CORE)
@Component
@Lazy
public class UserManagementInfoContributor implements InfoContributor {

    private final Optional<Boolean> needsToAcceptTerms;

    private final Optional<Boolean> registrationEnabled;

    private final Optional<Pattern> allowedEmailPattern;

    private final Optional<String> allowedEmailPatternReadable;

    private final Optional<Pattern> allowedLdapUsernamePattern;

    private final Optional<Pattern> allowedCourseRegistrationUsernamePattern;

    private final Optional<String> accountName;

    private final boolean ldapEnabled;

    public UserManagementInfoContributor(@Value("${artemis.user-management.accept-terms:#{null}}") Optional<Boolean> needsToAcceptTerms,
            @Value("${artemis.user-management.registration.enabled:#{null}}") Optional<Boolean> registrationEnabled,
            @Value("${artemis.user-management.registration.allowed-email-pattern:#{null}}") Optional<Pattern> allowedEmailPattern,
            @Value("${artemis.user-management.registration.allowed-email-pattern-readable:#{null}}") Optional<String> allowedEmailPatternReadable,
            @Value("${artemis.user-management.ldap.allowed-username-pattern:#{null}}") Optional<Pattern> allowedLdapUsernamePattern,
            @Value("${artemis.user-management.course-registration.allowed-username-pattern:#{null}}") Optional<Pattern> allowedCourseRegistrationUsernamePattern,
            @Value("${artemis.user-management.login.account-name:#{null}}") Optional<String> accountName,
            @Value("${artemis.user-management.ldap.enabled:false}") boolean ldapEnabled) {
        this.needsToAcceptTerms = needsToAcceptTerms;
        this.registrationEnabled = registrationEnabled;
        this.allowedEmailPattern = allowedEmailPattern;
        this.allowedEmailPatternReadable = allowedEmailPatternReadable;
        this.allowedLdapUsernamePattern = allowedLdapUsernamePattern;
        this.allowedCourseRegistrationUsernamePattern = allowedCourseRegistrationUsernamePattern;
        this.accountName = accountName;
        this.ldapEnabled = ldapEnabled;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.REGISTRATION_ENABLED, registrationEnabled.orElse(Boolean.FALSE));
        builder.withDetail(Constants.NEEDS_TO_ACCEPT_TERMS, needsToAcceptTerms.orElse(Boolean.FALSE));
        allowedEmailPattern.ifPresent(pattern -> builder.withDetail(Constants.ALLOWED_EMAIL_PATTERN, pattern.toString()));
        allowedEmailPatternReadable.ifPresent(patternReadable -> builder.withDetail(Constants.ALLOWED_EMAIL_PATTERN_READABLE, patternReadable));
        if (ldapEnabled) {
            allowedLdapUsernamePattern.ifPresent(pattern -> builder.withDetail(Constants.ALLOWED_LDAP_USERNAME_PATTERN, pattern));
        }
        allowedCourseRegistrationUsernamePattern.ifPresent(pattern -> builder.withDetail(Constants.ALLOWED_COURSE_REGISTRATION_USERNAME_PATTERN, pattern));
        accountName.ifPresent(account -> builder.withDetail(Constants.ACCOUNT_NAME, account));
    }
}
