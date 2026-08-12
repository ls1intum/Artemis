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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
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

    @Mock
    private PlatformTransactionManager transactionManager;

    @TempDir
    private Path tempDirectory;

    private AttachmentService attachmentService;

    private Attachment attachment;

    @BeforeEach
    void setUp() {
        FilePathConverter.setFileUploadPath(tempDirectory);
        attachmentService = new AttachmentService(attachmentRepository, slideRepository, fileService, tempFileUtilService, new TransactionAfterCommitService(), transactionManager);
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

    @Test
    void regenerationLocksPersistedAttachmentBeforeReadingVisibility() {
        attachment.setId(7L);
        attachment.setStudentVersion(null);
        when(attachmentRepository.findByIdWithPessimisticWriteLock(attachment.getId())).thenReturn(java.util.Optional.of(attachment));
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNull(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            attachmentService.regenerateStudentVersion(attachment);
        }
        finally {
            TransactionSynchronizationManager.clear();
        }

        verify(attachmentRepository).findByIdWithPessimisticWriteLock(attachment.getId());
    }

    @Test
    void regenerationFailurePreservesOriginalCause() {
        attachment.setLink("attachments/attachment-unit/42/missing.pdf");
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNull(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of(new Slide()));

        assertThatThrownBy(() -> attachmentService.regenerateStudentVersion(attachment)).isInstanceOf(InternalServerErrorException.class).hasCauseInstanceOf(IOException.class);
    }

    @Test
    void lectureAttachmentPersistenceFailureKeepsOldFile() {
        long lectureId = 7L;
        var lecture = new Lecture();
        lecture.setId(lectureId);
        attachment.setId(8L);
        attachment.setLecture(lecture);
        attachment.setLink("attachments/lecture/7/old.pdf");
        when(attachmentRepository.findByIdOrElseThrow(attachment.getId())).thenReturn(attachment);
        doThrow(new IllegalStateException("database unavailable")).when(attachmentRepository).save(attachment);
        var replacement = new MockMultipartFile("file", "new.pdf", "application/pdf", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> attachmentService.updateLectureAttachment(attachment.getId(), attachment, replacement)).isInstanceOf(IllegalStateException.class);

        Path oldPath = FilePathConverter.fileSystemPathForExternalUri(URI.create("attachments/lecture/7/old.pdf"), FilePathType.LECTURE_ATTACHMENT);
        verify(fileService, never()).schedulePathForDeletion(oldPath, 0);
        verify(fileService, never()).evictCacheForPath(oldPath);
    }

    @Test
    void studentVersionDeletionFailurePreservesOriginalCause() {
        attachment.setStudentVersion("%invalid");
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNull(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> attachmentService.regenerateStudentVersion(attachment)).isInstanceOf(InternalServerErrorException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uploadedStudentVersionAlwaysUsesNewPath() throws IOException {
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);

        attachmentService.replaceUploadedStudentVersionFile(new byte[] { 1, 2, 3 }, attachment, ATTACHMENT_VIDEO_UNIT_ID, "old.pdf");

        assertThat(attachment.getStudentVersion()).isNotEqualTo(OLD_STUDENT_VERSION);
        assertThat(attachment.getStudentVersion()).endsWith(".pdf");
    }

}
