package de.tum.cit.aet.artemis.communication.notifications.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.User;

public class DataExportSuccessfulMailTest extends AbstractMailContentTest {

    @Test
    void testThatVariablesAreInjectedIntoTheTemplate() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();
        recipient.setLogin("test_login");
        recipient.setResetKey("test_reset_key");
        Set<DataExport> dataExports = createThreeDataExportsWithThreeDifferentUsers();

        // Act:
        mailService.sendSuccessfulDataExportsEmailToAdmin(recipient, dataExports);

        // Assert:
        String capturedContent = getGeneratedEmailTemplateText();
        assertThat(capturedContent).contains("test_subject_1");
        assertThat(capturedContent).contains("test_subject_2");
        assertThat(capturedContent).contains("test_subject_3");
    }

    @NotNull
    private static Set<DataExport> createThreeDataExportsWithThreeDifferentUsers() {
        User dataExportSubjectUser1 = new User();
        User dataExportSubjectUser2 = new User();
        User dataExportSubjectUser3 = new User();
        dataExportSubjectUser1.setLogin("test_subject_1");
        dataExportSubjectUser2.setLogin("test_subject_2");
        dataExportSubjectUser3.setLogin("test_subject_3");
        DataExport dataExport1 = new DataExport();
        DataExport dataExport2 = new DataExport();
        DataExport dataExport3 = new DataExport();
        dataExport1.setUser(dataExportSubjectUser1);
        dataExport2.setUser(dataExportSubjectUser2);
        dataExport3.setUser(dataExportSubjectUser3);

        return Set.of(dataExport1, dataExport2, dataExport3);
    }
}
