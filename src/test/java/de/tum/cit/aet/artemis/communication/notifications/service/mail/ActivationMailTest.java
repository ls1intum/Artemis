package de.tum.cit.aet.artemis.communication.notifications.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.User;

public class ActivationMailTest extends AbstractMailContentTest {

    @Test
    void testThatVariablesAreInjectedIntoTheTemplate() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();
        recipient.setLogin("test_student");
        recipient.setActivationKey("test_key");

        // Act:
        mailService.sendActivationEmail(recipient);

        // Assert:
        String capturedContent = getGeneratedEmailTemplateText();
        assertThat(capturedContent).contains("test_student");
        assertThat(capturedContent).contains("test_key");
    }
}
