package de.tum.in.www1.artemis.service.user;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
public class UserManagementInfoContributor implements InfoContributor {

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

    @Value("${artemis.user-management.organizations.enable-multiple-organizations:#{null}}")
    private Optional<Boolean> enabledMultipleOrganizations;

    @Value("${artemis.user-management.login.account-name:#{null}}")
    private Optional<String> accountName;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.REGISTRATION_ENABLED, registrationEnabled.orElse(Boolean.FALSE));
        allowedEmailPattern.ifPresent(pattern -> builder.withDetail(Constants.ALLOWED_EMAIL_PATTERN, pattern.toString()));
        allowedEmailPatternReadable.ifPresent(patternReadable -> builder.withDetail(Constants.ALLOWED_EMAIL_PATTERN_READABLE, patternReadable));
        allowedLdapUsernamePattern.ifPresent(pattern -> builder.withDetail(Constants.ALLOWED_LDAP_USERNAME_PATTERN, pattern));
        allowedCourseRegistrationUsernamePattern.ifPresent(pattern -> builder.withDetail(Constants.ALLOWED_COURSE_REGISTRATION_USERNAME_PATTERN, pattern));
        enabledMultipleOrganizations.ifPresent(pattern -> builder.withDetail(Constants.ENABLED_MULTIPLE_ORGANIZATIONS, pattern));
        accountName.ifPresent(accountName -> builder.withDetail(Constants.ACCOUNT_NAME, accountName));
    }
}
