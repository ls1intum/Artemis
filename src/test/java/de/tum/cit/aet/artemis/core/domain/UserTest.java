package de.tum.cit.aet.artemis.core.domain;

import static de.tum.cit.aet.artemis.core.domain.User.IRIS_BOT_LOGIN;
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
}
