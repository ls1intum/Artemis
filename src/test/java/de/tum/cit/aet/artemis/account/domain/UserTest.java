package de.tum.cit.aet.artemis.account.domain;

import static de.tum.cit.aet.artemis.account.domain.User.IRIS_BOT_LOGIN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserTest {

    @Test
    void isBot_withBotLogin_returnsTrue() {
        User user = new User();
        user.setLogin(IRIS_BOT_LOGIN);
        assertThat(user.isBot()).isTrue();
    }

    @Test
    void isBot_withRegularLogin_returnsFalse() {
        User user = new User();
        user.setLogin("student1");
        assertThat(user.isBot()).isFalse();
    }

    @Test
    void isBot_withNullLogin_returnsFalse() {
        User user = new User();
        user.setLogin(null);
        assertThat(user.isBot()).isFalse();
    }

    @Test
    void setEmail_normalizesCase() {
        User user = new User();

        user.setEmail("Student.Name@Example.COM");

        assertThat(user.getEmail()).isEqualTo("student.name@example.com");
    }

    @Test
    void setEmail_withNull_keepsNull() {
        User user = new User();

        user.setEmail(null);

        assertThat(user.getEmail()).isNull();
    }

    @Test
    void shouldNormalizeBlankEmailToNull() {
        User user = new User();

        user.setEmail("  ");

        assertThat(user.getEmail()).isNull();
    }

    @Test
    void constructorNormalizesEmail() {
        User user = new User(1L, "student", "Student", "Test", "en", "Student@Example.COM");

        assertThat(user.getEmail()).isEqualTo("student@example.com");
    }

    /**
     * An LTI platform such as Open edX may omit the given_name and family_name claims, so an account can carry no name at all. The
     * display name must then fall back to the login: it is written into git commit identities, where JGit rejects null, and shown
     * wherever Artemis names a user.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "null", value = { "Jane, Doe, Jane Doe", "Jane, null, Jane", "Jane, '', Jane", "null, Doe, Doe", "null, null, edx_jane", "'  ', '  ', edx_jane",
            "'', '', edx_jane" })
    void getName_fallsBackToLoginWhenNoNameIsSet(String firstName, String lastName, String expected) {
        User user = new User();
        user.setLogin("edx_jane");
        user.setFirstName(firstName);
        user.setLastName(lastName);

        assertThat(user.getName()).isEqualTo(expected);
    }
}
