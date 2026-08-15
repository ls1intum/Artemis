package de.tum.cit.aet.artemis.account.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.config.Constants;

class UserManagementInfoContributorTest {

    private UserManagementInfoContributor contributor;

    @BeforeEach
    void setUp() {
        contributor = new UserManagementInfoContributor();

        ReflectionTestUtils.setField(contributor, "needsToAcceptTerms", Optional.empty());
        ReflectionTestUtils.setField(contributor, "registrationEnabled", Optional.empty());
        ReflectionTestUtils.setField(contributor, "allowedEmailPattern", Optional.empty());
        ReflectionTestUtils.setField(contributor, "allowedEmailPatternReadable", Optional.empty());
        ReflectionTestUtils.setField(contributor, "allowedLdapUsernamePattern", Optional.empty());
        ReflectionTestUtils.setField(contributor, "allowedCourseRegistrationUsernamePattern", Optional.empty());
        ReflectionTestUtils.setField(contributor, "accountName", Optional.empty());
        ReflectionTestUtils.setField(contributor, "ldapEnabled", false);
    }

    @Test
    void testContribute_whenLdapDisabled_doesNotIncludeAllowedLdapUsernamePattern() {
        Pattern pattern = Pattern.compile("^([a-z]{2}\\d{2}[a-z]{3})$");
        ReflectionTestUtils.setField(contributor, "allowedLdapUsernamePattern", Optional.of(pattern));
        ReflectionTestUtils.setField(contributor, "ldapEnabled", false);

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        assertThat(info.getDetails()).doesNotContainKey(Constants.ALLOWED_LDAP_USERNAME_PATTERN);
    }

    @Test
    void testContribute_whenLdapEnabled_includesAllowedLdapUsernamePattern() {
        Pattern pattern = Pattern.compile("^([a-z]{2}\\d{2}[a-z]{3})$");
        ReflectionTestUtils.setField(contributor, "allowedLdapUsernamePattern", Optional.of(pattern));
        ReflectionTestUtils.setField(contributor, "ldapEnabled", true);

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        assertThat(info.getDetails()).containsEntry(Constants.ALLOWED_LDAP_USERNAME_PATTERN, pattern);
    }

    @Test
    void testContribute_withOtherConfigurations_includesExpectedDetails() {
        ReflectionTestUtils.setField(contributor, "registrationEnabled", Optional.of(true));
        ReflectionTestUtils.setField(contributor, "needsToAcceptTerms", Optional.of(true));
        ReflectionTestUtils.setField(contributor, "allowedEmailPattern", Optional.of(Pattern.compile(".+@tum\\.de")));
        ReflectionTestUtils.setField(contributor, "allowedEmailPatternReadable", Optional.of("@tum.de"));
        ReflectionTestUtils.setField(contributor, "accountName", Optional.of("TUM"));

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        assertThat(info.getDetails()).containsEntry(Constants.REGISTRATION_ENABLED, true);
        assertThat(info.getDetails()).containsEntry(Constants.NEEDS_TO_ACCEPT_TERMS, true);
        assertThat(info.getDetails()).containsEntry(Constants.ALLOWED_EMAIL_PATTERN, ".+@tum\\.de");
        assertThat(info.getDetails()).containsEntry(Constants.ALLOWED_EMAIL_PATTERN_READABLE, "@tum.de");
        assertThat(info.getDetails()).containsEntry(Constants.ACCOUNT_NAME, "TUM");
    }
}
