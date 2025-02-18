package de.tum.cit.aet.artemis.communication.notifications.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.User;

class DataExportFailedMailTest extends AbstractMailContentTest {

    /**
     * Test that the variables injected in the template are used in the generated HTML content.
     */
    @Test
    void testThatVariablesAreInjectedIntoTheTemplate() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();
        recipient.setLogin("test_login");
        String subject = createExpectedSubject(recipient, "email.dataExportFailedAdmin.title", "test_subject");

        User dataExporSubjecttUser = new User();
        dataExporSubjecttUser.setLogin("test_subject");

        DataExport dataExport = new DataExport();
        dataExport.setUser(dataExporSubjecttUser);

        Exception reason = new Exception("test_reason");

        // Act:
        mailService.sendDataExportFailedEmailToAdmin(recipient, dataExport, reason);

        // Assert:
        String capturedContent = getGeneratedEmailTemplateText(subject);
        assertThat(capturedContent).contains("test_login");
        assertThat(capturedContent).contains("test_subject");
        assertThat(capturedContent).contains("test_reason");
    }
}
