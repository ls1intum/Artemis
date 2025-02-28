package de.tum.cit.aet.artemis.communication.notifications.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.User;

class PasswordResetMailTest extends AbstractMailContentTest {

    /**
     * Test that the variables injected in the template are used in the generated HTML content.
     */
    @Test
    void testThatVariablesAreInjectedIntoTheTemplate() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();
        recipient.setLogin("test_login");
        recipient.setResetKey("test_reset_key");
        String subject = createExpectedSubject(recipient, "email.reset.title");

        // Act:
        mailService.sendPasswordResetMail(recipient);

        // Assert:
        String capturedContent = getGeneratedEmailTemplateText(subject);
        assertThat(capturedContent).contains("test_login");
        assertThat(capturedContent).contains("test_reset_key");
    }

    @Test
    void testThatExceptionIsThrownWhenResetKeyIsMissing() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();

        // Act and Assert:
        assertThatThrownBy(() -> mailService.sendPasswordResetMail(recipient)).isInstanceOf(IllegalStateException.class).hasMessage("Reset key is required");
    }
}
