package de.tum.cit.aet.artemis.account.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import de.tum.cit.aet.artemis.core.config.Constants;

class UserManagementInfoContributorTest {

    @Test
    void testContribute_whenLdapDisabled_doesNotIncludeAllowedLdapUsernamePattern() {
        Pattern pattern = Pattern.compile("^([a-z]{2}\\d{2}[a-z]{3})$");
        UserManagementInfoContributor contributor = new UserManagementInfoContributor(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(pattern),
                Optional.empty(), Optional.empty(), false // ldapEnabled = false
        );

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        assertThat(info.getDetails()).doesNotContainKey(Constants.ALLOWED_LDAP_USERNAME_PATTERN);
    }

    @Test
    void testContribute_whenLdapEnabled_includesAllowedLdapUsernamePattern() {
        Pattern pattern = Pattern.compile("^([a-z]{2}\\d{2}[a-z]{3})$");
        UserManagementInfoContributor contributor = new UserManagementInfoContributor(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(pattern),
                Optional.empty(), Optional.empty(), true // ldapEnabled = true
        );

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        assertThat(info.getDetails()).containsEntry(Constants.ALLOWED_LDAP_USERNAME_PATTERN, pattern);
    }

    @Test
    void testContribute_withOtherConfigurations_includesExpectedDetails() {
        Pattern emailPattern = Pattern.compile(".+@tum\\.de");
        UserManagementInfoContributor contributor = new UserManagementInfoContributor(Optional.of(true), Optional.of(true), Optional.of(emailPattern), Optional.of("@tum.de"),
                Optional.empty(), Optional.empty(), Optional.of("TUM"), false);

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
