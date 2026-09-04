package de.tum.cit.aet.artemis.account.domain;

import static de.tum.cit.aet.artemis.account.domain.User.IRIS_BOT_LOGIN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
