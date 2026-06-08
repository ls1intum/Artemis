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
    void fromCopiesIdNameAndImageUrlButOmitsLoginFirstNameLastName() {
        // login, firstName, lastName, email, registrationNumber are intentionally not record components — they
        // were not in the pre-refactor wire shape (@JsonIncludeProperties({"id","name","imageUrl","bot"})) on
        // Posting.author, and exposing them now would widen the data-minimization guarantee the existing tests
        // (assertSensitiveInformationHidden) rely on.
        User user = new User(42L, "ada.lovelace", "Ada", "Lovelace", "en", "ada@example.org");
        user.setImageUrl("/images/ada.png");

        UserSummaryDTO summary = UserSummaryDTO.from(user);

        assertThat(summary).isNotNull();
        assertThat(summary.id()).isEqualTo(42L);
        // getName() composes first + last
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
