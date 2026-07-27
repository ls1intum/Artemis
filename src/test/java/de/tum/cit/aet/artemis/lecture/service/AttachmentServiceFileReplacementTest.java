package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceFileReplacementTest {

    private static final long ATTACHMENT_VIDEO_UNIT_ID = 42L;

    private static final String OLD_STUDENT_VERSION = "attachments/attachment-unit/42/student/old.pdf";

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private SlideTestRepository slideRepository;

    @Mock
    private FileService fileService;

    @Mock
    private TempFileUtilService tempFileUtilService;

    @TempDir
    private Path tempDirectory;

    private AttachmentService attachmentService;

    private Attachment attachment;

    @BeforeEach
    void setUp() {
        FilePathConverter.setFileUploadPath(tempDirectory);
        attachmentService = new AttachmentService(attachmentRepository, slideRepository, fileService, tempFileUtilService, new TransactionAfterCommitService());
        var attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setId(ATTACHMENT_VIDEO_UNIT_ID);
        attachment = new Attachment();
        attachment.setName("lecture.pdf");
        attachment.setStudentVersion(OLD_STUDENT_VERSION);
        attachment.setAttachmentVideoUnit(attachmentVideoUnit);
    }

    @Test
    void replacementPersistenceFailureKeepsOldReferenceAndCleansUpNewFile() throws IOException {
        doThrow(new IllegalStateException("database unavailable")).when(attachmentRepository).saveAndFlush(attachment);

        assertThatThrownBy(() -> attachmentService.replaceStudentVersionFile(new byte[] { 1, 2, 3 }, attachment, ATTACHMENT_VIDEO_UNIT_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(attachment.getStudentVersion()).isEqualTo(OLD_STUDENT_VERSION);
        var installedPath = ArgumentCaptor.forClass(Path.class);
        verify(tempFileUtilService).replaceFileAtomically(any(Path.class), installedPath.capture(), any(byte[].class));
        verify(fileService).schedulePathForDeletion(installedPath.getValue(), 0);
        Path oldPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(OLD_STUDENT_VERSION), FilePathType.STUDENT_VERSION_SLIDES);
        verify(fileService, never()).schedulePathForDeletion(oldPath, 0);
    }

    @Test
    void clearingPersistenceFailureKeepsOldReferenceAndOldFile() {
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNull(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of());
        doThrow(new IllegalStateException("database unavailable")).when(attachmentRepository).saveAndFlush(attachment);

        assertThatThrownBy(() -> attachmentService.regenerateStudentVersion(attachment)).isInstanceOf(IllegalStateException.class);

        assertThat(attachment.getStudentVersion()).isEqualTo(OLD_STUDENT_VERSION);
        verify(fileService, never()).schedulePathForDeletion(any(Path.class), anyLong());
    }
}
