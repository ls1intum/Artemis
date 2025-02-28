package de.tum.cit.aet.artemis.communication.notifications.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.User;

class ActivationMailTest extends AbstractMailContentTest {

    /**
     * Test that the variables injected in the template are used in the generated HTML content.
     */
    @Test
    void testThatVariablesAreInjectedIntoTheTemplate() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();
        recipient.setLogin("test_student");
        recipient.setActivationKey("test_key");
        String subject = createExpectedSubject(recipient, "email.activation.title");

        // Act:
        mailService.sendActivationEmail(recipient);

        // Assert:
        String capturedContent = getGeneratedEmailTemplateText(subject);
        assertThat(capturedContent).contains("test_student");
        assertThat(capturedContent).contains("test_key");
    }

    @Test
    void testThatExceptionIsThrownWhenActivationKeyIsMissing() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();
        recipient.setLogin("test_student");

        // Act and Assert:
        assertThatThrownBy(() -> mailService.sendActivationEmail(recipient)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Activation key is required for account activation");
    }
}
