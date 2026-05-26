package de.tum.cit.aet.artemis.account;

import static de.tum.cit.aet.artemis.account.domain.User.IRIS_BOT_LOGIN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.UserSummaryDTO;

class UserSummaryDTOTest {

    @Test
    void fromReturnsNullForNullInput() {
        assertThat(UserSummaryDTO.from(null)).isNull();
    }

    @Test
    void fromCopiesAllVisibleFields() {
        User user = new User(42L, "ada.lovelace", "Ada", "Lovelace", "en", "ada@example.org");
        user.setImageUrl("/images/ada.png");

        UserSummaryDTO summary = UserSummaryDTO.from(user);

        assertThat(summary).isNotNull();
        assertThat(summary.id()).isEqualTo(42L);
        assertThat(summary.login()).isEqualTo("ada.lovelace");
        assertThat(summary.firstName()).isEqualTo("Ada");
        assertThat(summary.lastName()).isEqualTo("Lovelace");
        assertThat(summary.name()).isEqualTo("Ada Lovelace");
        assertThat(summary.imageUrl()).isEqualTo("/images/ada.png");
        assertThat(summary.bot()).isFalse();
    }

    @Test
    void fromComposesNameFromFirstNameAloneWhenLastNameMissing() {
        User user = new User();
        user.setFirstName("Ada");

        UserSummaryDTO summary = UserSummaryDTO.from(user);

        assertThat(summary).isNotNull();
        assertThat(summary.name()).isEqualTo("Ada");
        assertThat(summary.lastName()).isNull();
    }

    @Test
    void fromDetectsIrisBotAccount() {
        User bot = new User();
        bot.setLogin(IRIS_BOT_LOGIN);

        UserSummaryDTO summary = UserSummaryDTO.from(bot);

        assertThat(summary).isNotNull();
        assertThat(summary.bot()).isTrue();
    }
}
