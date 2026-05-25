package de.tum.cit.aet.artemis.account.dto;

import static de.tum.cit.aet.artemis.account.domain.User.IRIS_BOT_LOGIN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;

class UserMapperTest {

    @Test
    void toSummaryReturnsNullForNullInput() {
        assertThat(UserMapper.toSummary(null)).isNull();
    }

    @Test
    void toSummaryCopiesAllVisibleFields() {
        User user = new User(42L, "ada.lovelace", "Ada", "Lovelace", "en", "ada@example.org");
        user.setImageUrl("/images/ada.png");

        UserSummaryDTO summary = UserMapper.toSummary(user);

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
    void toSummaryComposesNameFromFirstNameAloneWhenLastNameMissing() {
        User user = new User();
        user.setFirstName("Ada");

        UserSummaryDTO summary = UserMapper.toSummary(user);

        assertThat(summary).isNotNull();
        assertThat(summary.name()).isEqualTo("Ada");
        assertThat(summary.lastName()).isNull();
    }

    @Test
    void toSummaryDetectsIrisBotAccount() {
        User bot = new User();
        bot.setLogin(IRIS_BOT_LOGIN);

        UserSummaryDTO summary = UserMapper.toSummary(bot);

        assertThat(summary).isNotNull();
        assertThat(summary.bot()).isTrue();
    }
}
