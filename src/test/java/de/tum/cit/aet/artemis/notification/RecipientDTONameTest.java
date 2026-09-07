package de.tum.cit.aet.artemis.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationRecipientDTO;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;

/**
 * The mail templates render {@code getName()} of both recipient DTOs. An account without name claims (see #13537) must show its
 * login there, not "null Doe" or nothing.
 */
class RecipientDTONameTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = { "Jane, Doe, Jane Doe", "null, Doe, Doe", "Jane, null, Jane", "null, null, edx_jane", "'', '', edx_jane" })
    void recipientNamesFallBackToLogin(String firstName, String lastName, String expected) {
        User user = new User();
        user.setLogin("edx_jane");
        user.setEmail("edx_jane@example.com");
        user.setFirstName(firstName);
        user.setLastName(lastName);

        assertThat(MailRecipientDTO.from(user).getName()).isEqualTo(expected);
        assertThat(MailRecipientDTO.withRecoveryKey(user, "activation", null).getName()).isEqualTo(expected);
        assertThat(CourseNotificationRecipientDTO.from(user).getName()).isEqualTo(expected);
    }
}
