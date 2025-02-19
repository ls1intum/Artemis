package de.tum.cit.aet.artemis.communication.notifications.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

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

    @Test
    void testThatExceptionIsThrownWhenPassedExceptionIsNull() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();
        DataExport dataExport = new DataExport();

        // Act and Assert:
        assertThatThrownBy(() -> mailService.sendDataExportFailedEmailToAdmin(recipient, dataExport, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parameters cannot be null");
    }

    @Test
    void testThatExceptionIsThrownWhenPassedDataExportIsNull() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();

        // Act and Assert:
        assertThatThrownBy(() -> mailService.sendDataExportFailedEmailToAdmin(recipient, null, new Exception())).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parameters cannot be null");
    }

    @Test
    void testThatExceptionIsThrownWhenUserOfTheDataExportIsNull() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();

        // Act and Assert:
        assertThatThrownBy(() -> mailService.sendDataExportFailedEmailToAdmin(recipient, new DataExport(), new Exception())).isInstanceOf(IllegalStateException.class)
                .hasMessage("DataExport user cannot be null");
    }
}
