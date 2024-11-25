package de.tum.cit.aet.artemis.communication.notifications.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.User;

public class DataExportFailedMailTest extends AbstractMailContentTest {

    @Test
    void testThatVariablesAreInjectedIntoTheTemplate() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();
        recipient.setLogin("test_login");

        User dataExporSubjecttUser = new User();
        dataExporSubjecttUser.setLogin("test_subject");

        DataExport dataExport = new DataExport();
        dataExport.setUser(dataExporSubjecttUser);

        Exception reason = new Exception("test_reason");

        // Act:
        mailService.sendDataExportFailedEmailToAdmin(recipient, dataExport, reason);

        // Assert:
        String capturedContent = getGeneratedEmailTemplateText();
        assertThat(capturedContent).contains("test_login");
        assertThat(capturedContent).contains("test_subject");
        assertThat(capturedContent).contains("test_reason");
    }
}
