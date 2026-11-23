package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.domain.InitialCommitAuthorMode;
import de.tum.cit.aet.artemis.course.dto.InitialCommitAuthorDTO;

class InitialCommitAuthorTest {

    @Test
    void validate_acceptsACustomIdentity() {
        assertThatCode(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, "Course Team", "noreply@university.example").validate()).doesNotThrowAnyException();
        assertThatCode(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, "Jürgen Müller-Lüdenscheidt", "j.mueller+course@sub.university.example").validate())
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "a@b.c", "a@example.com", "a@b..c", "first.last+tag@sub.example.org" })
    void validate_acceptsAnAddressWithAnInnerDotInTheDomain(String email) {
        assertThatCode(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, "Course Team", email).validate()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "a@b.", "a@.b", "a@.", "a@b", "a@.example.com", "a@example.", "a@b.c." })
    void validate_rejectsADomainWithoutAnInnerDot(String email) {
        assertThatThrownBy(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, "Course Team", email).validate()).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void validate_ignoresTheIdentityOfTheOtherModes() {
        assertThatCode(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.EXERCISE_CREATOR, "<bad>\n", "not an email").validate()).doesNotThrowAnyException();
        assertThatCode(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.PLACEHOLDER, null, null).validate()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "Team\nauthor Forged <forged@example.org>", "Te\ram", "Team <team>", "Team>", "Team\u0000", "Team" + (char) 0x2028 + "Two", "Te\u0085am",
            "Team\u202E", "Te\u200Bam" })
    void validate_rejectsANameThatIsBlankOrCouldInjectGitHeaderData(String name) {
        assertThatThrownBy(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, name, "team@example.org").validate()).isInstanceOf(BadRequestAlertException.class)
                .extracting(exception -> ((BadRequestAlertException) exception).getErrorKey()).isEqualTo("invalidInitialCommitAuthorName");
    }

    @Test
    void validate_rejectsAMissingOrTooLongName() {
        assertThatThrownBy(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, null, "team@example.org").validate()).isInstanceOf(BadRequestAlertException.class);
        String tooLong = "x".repeat(101);
        assertThatThrownBy(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, tooLong, "team@example.org").validate()).isInstanceOf(BadRequestAlertException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "team", "team@example", "@example.org", "team@@example.org", "a@b@example.org", "team@example.org\nauthor x", "team@example.org>",
            "<team@example.org", "tea m@example.org", "team@exa mple.org", "tea\u00A0m@example.org", "team@exa\u00A0mple.org", "te\u0085am@example.org", "team@example.org\u202E",
            "team@exam\u2028ple.org" })
    void validate_rejectsAnInvalidEmail(String email) {
        assertThatThrownBy(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, "Course Team", email).validate()).isInstanceOf(BadRequestAlertException.class)
                .extracting(exception -> ((BadRequestAlertException) exception).getErrorKey()).isEqualTo("invalidInitialCommitAuthorEmail");
    }

    @Test
    void validate_rejectsAMissingOrTooLongEmail() {
        assertThatThrownBy(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, "Course Team", null).validate()).isInstanceOf(BadRequestAlertException.class);
        String tooLong = "a".repeat(250) + "@example.org";
        assertThatThrownBy(() -> new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, "Course Team", tooLong).validate()).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void stripped_removesSurroundingWhitespace() {
        var stripped = new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, "  Course Team ", " team@example.org  ").stripped();

        assertThat(stripped.name()).isEqualTo("Course Team");
        assertThat(stripped.email()).isEqualTo("team@example.org");
    }

    @Test
    void configuration_keepsTheIdentityOnlyForTheCustomMode() {
        var configuration = new CourseConfiguration();
        assertThat(configuration.getInitialCommitAuthorMode()).isEqualTo(InitialCommitAuthorMode.EXERCISE_CREATOR);

        configuration.setInitialCommitAuthor(InitialCommitAuthorMode.CUSTOM, "Course Team", "team@example.org");
        assertThat(InitialCommitAuthorDTO.of(configuration)).isEqualTo(new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, "Course Team", "team@example.org"));

        configuration.setInitialCommitAuthor(InitialCommitAuthorMode.PLACEHOLDER, "Course Team", "team@example.org");
        assertThat(configuration.getInitialCommitAuthorName()).isNull();
        assertThat(configuration.getInitialCommitAuthorEmail()).isNull();
    }
}
