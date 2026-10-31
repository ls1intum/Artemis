package de.tum.cit.aet.artemis.admin.service.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.DataExport;
import de.tum.cit.aet.artemis.admin.domain.DataExportState;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.test_repository.DataExportTestRepository;
import de.tum.cit.aet.artemis.notification.service.notifications.MailService;

class DataExportHomeNotificationTest {

    @TempDir
    private Path directory;

    private final DataExportTestRepository repository = mock(DataExportTestRepository.class);

    private final MailService mail = mock(MailService.class);

    private final DataExportCourseHomeService home = mock(DataExportCourseHomeService.class);

    private final TempFileUtilService temporary = mock(TempFileUtilService.class);

    private final ResourceLoaderService resources = mock(ResourceLoaderService.class);

    private final ZipFileService zip = mock(ZipFileService.class);

    private DataExportCreationService service;

    private DataExport export;

    private Path working;

    @BeforeEach
    void setup() throws Exception {
        working = Files.createDirectory(directory.resolve("working"));
        var readme = directory.resolve("README.md");
        FileUtils.writeStringToFile(readme.toFile(), "Personal data export", StandardCharsets.UTF_8);
        var archive = directory.resolve("export.zip");
        FileUtils.writeByteArrayToFile(archive.toFile(), new byte[0]);
        when(repository.save(any(DataExport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(temporary.createTempDirectory(any(Path.class), anyString())).thenReturn(working);
        when(resources.getResourceFilePath(any(Path.class))).thenReturn(readme);
        when(zip.createZipFileWithFolderContent(any(Path.class), any(Path.class), isNull())).thenReturn(archive);
        service = new DataExportCreationService(directory, zip, mock(FileService.class), repository, mail, mock(DataExportExerciseCreationService.class),
                mock(DataExportExamCreationService.class), mock(DataExportCommunicationDataService.class), mock(DataExportScienceEventService.class),
                mock(DataExportIrisService.class), mock(DataExportLearnerProfileService.class), mock(DataExportCompetencyProgressService.class),
                mock(DataExportTutorialGroupService.class), mock(DataExportPresentationAssessmentService.class), resources, temporary, home);
        ReflectionTestUtils.setField(service, "adminEmail", "admin@example.org");
        var user = new User();
        user.setId(7L);
        user.setLogin("home-export");
        user.setEmail("student@example.org");
        user.setLangKey("en");
        export = new DataExport();
        export.setId(42L);
        export.setUser(user);
    }

    @Test
    void aCompletedArchiveIncludesTheHomeExportBeforeNotifyingTheStudent() throws Exception {
        assertThat(service.createDataExport(export)).isTrue();
        verify(home).createCourseHomeExport(7, working);
        assertThat(export.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
        assertThat(export.getFilePath()).endsWith("export.zip");
        assertThat(export.getCreationFinishedDate()).isNotNull();
        verify(mail).sendDataExportCreatedEmail(any(), any());
        verify(mail, never()).sendDataExportEmailFailedEmailToAdmin(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "admin@example.org", "" })
    void notificationFailurePreservesTheCompletedArchiveAndRecordsTheRightState(String admin) throws Exception {
        ReflectionTestUtils.setField(service, "adminEmail", admin);
        doThrow(new IllegalStateException("mail unavailable")).when(mail).sendDataExportCreatedEmail(any(), any());
        assertThat(service.createDataExport(export)).isFalse();
        verify(home).createCourseHomeExport(7, working);
        assertThat(export.getDataExportState()).isEqualTo(DataExportState.EMAIL_FAILED);
        assertThat(Path.of(export.getFilePath())).exists();
        assertThat(export.getCreationFinishedDate()).isNotNull();
        if (admin.isBlank())
            verify(mail, never()).sendDataExportEmailFailedEmailToAdmin(any(), any(), any());
        else
            verify(mail).sendDataExportEmailFailedEmailToAdmin(any(), any(), any());
    }

    @Test
    void aFailedHomeExportNeverPublishesAnIncompleteArchive() throws Exception {
        ReflectionTestUtils.setField(service, "adminEmail", "");
        doThrow(new IOException("Home export unavailable")).when(home).createCourseHomeExport(7, working);
        assertThat(service.createDataExport(export)).isFalse();
        assertThat(export.getDataExportState()).isEqualTo(DataExportState.FAILED);
        assertThat(export.getFilePath()).isNull();
        verify(mail, never()).sendDataExportCreatedEmail(any(), any());
        verify(zip, never()).createZipFileWithFolderContent(any(), any(), any());
    }
}
