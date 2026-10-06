package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;

/**
 * Covers the order in which a student version is written and removed: a file the database still references is never deleted, and a reference is only removed while it still
 * points at the file the caller started from.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceFileReplacementTest {

    private static final long ATTACHMENT_ID = 7L;

    private static final long ATTACHMENT_VIDEO_UNIT_ID = 42L;

    private static final String OLD_STUDENT_VERSION = "old.pdf";

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
        attachmentService = new AttachmentService(attachmentRepository, slideRepository, fileService, tempFileUtilService);
        var attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setId(ATTACHMENT_VIDEO_UNIT_ID);
        attachment = new Attachment();
        attachment.setId(ATTACHMENT_ID);
        attachment.setName("lecture");
        // A stored filename whose file does not exist, so generating a student version from it fails.
        attachment.setLink("missing.pdf");
        attachment.setAttachmentVideoUnit(attachmentVideoUnit);
        attachment.setStudentVersion(OLD_STUDENT_VERSION);
    }

    @Test
    void uploadedStudentVersionStoresReferenceBeforeDeletingOldFile() throws IOException {
        attachmentService.replaceUploadedStudentVersionFile(new byte[] { 1, 2, 3 }, attachment, ATTACHMENT_VIDEO_UNIT_ID, "student.pdf");

        var installedPath = ArgumentCaptor.forClass(Path.class);
        var order = inOrder(tempFileUtilService, attachmentRepository, fileService);
        order.verify(tempFileUtilService).replaceFileAtomically(any(Path.class), installedPath.capture(), any(byte[].class));
        order.verify(attachmentRepository).updateStudentVersion(eq(ATTACHMENT_ID), anyString());
        order.verify(fileService).schedulePathForDeletion(oldStudentVersionPath(), 0);

        String newFilename = installedPath.getValue().getFileName().toString();
        assertThat(newFilename).isNotEqualTo(OLD_STUDENT_VERSION).endsWith(".pdf");
        verify(attachmentRepository).updateStudentVersion(ATTACHMENT_ID, newFilename);
        assertThat(FileSystemLocation.storedFilename(attachment.getStudentVersion())).isEqualTo(newFilename);
    }

    @Test
    void failureToStoreReferenceDeletesNewFileAndKeepsOldOne() throws IOException {
        doThrow(new IllegalStateException("database unavailable")).when(attachmentRepository).updateStudentVersion(eq(ATTACHMENT_ID), anyString());

        assertThatThrownBy(() -> attachmentService.replaceUploadedStudentVersionFile(new byte[] { 1, 2, 3 }, attachment, ATTACHMENT_VIDEO_UNIT_ID, "student.pdf"))
                .isInstanceOf(IllegalStateException.class);

        var installedPath = ArgumentCaptor.forClass(Path.class);
        verify(tempFileUtilService).replaceFileAtomically(any(Path.class), installedPath.capture(), any(byte[].class));
        verify(fileService).schedulePathForDeletion(installedPath.getValue(), 0);
        verify(fileService, never()).schedulePathForDeletion(oldStudentVersionPath(), 0);
        assertThat(FileSystemLocation.storedFilename(attachment.getStudentVersion())).isEqualTo(OLD_STUDENT_VERSION);
    }

    @Test
    void regenerationWithoutHiddenSlidesRemovesUnchangedStudentVersion() {
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of());
        when(attachmentRepository.clearStudentVersionIfUnchanged(ATTACHMENT_ID, OLD_STUDENT_VERSION)).thenReturn(1);

        attachmentService.regenerateStudentVersion(attachment);

        verify(fileService).schedulePathForDeletion(oldStudentVersionPath(), 0);
        assertThat(attachment.getStudentVersion()).isNull();
    }

    @Test
    void regenerationWithoutHiddenSlidesKeepsConcurrentlyReplacedStudentVersion() {
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of());
        when(attachmentRepository.clearStudentVersionIfUnchanged(ATTACHMENT_ID, OLD_STUDENT_VERSION)).thenReturn(0);

        attachmentService.regenerateStudentVersion(attachment);

        verify(fileService, never()).schedulePathForDeletion(any(Path.class), anyLong());
    }

    @Test
    void failedRegenerationRemovesOutdatedStudentVersionWithoutFailingTheCaller() {
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of(hiddenSlide()));
        when(attachmentRepository.clearStudentVersionIfUnchanged(ATTACHMENT_ID, OLD_STUDENT_VERSION)).thenReturn(1);

        boolean regenerated = attachmentService.regenerateStudentVersionOrRemoveOutdated(attachment);

        assertThat(regenerated).isFalse();
        verify(fileService).schedulePathForDeletion(oldStudentVersionPath(), 0);
        assertThat(attachment.getStudentVersion()).isNull();
    }

    @Test
    void failedRegenerationKeepsStudentVersionPublishedConcurrently() {
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of(hiddenSlide()));
        when(attachmentRepository.clearStudentVersionIfUnchanged(ATTACHMENT_ID, OLD_STUDENT_VERSION)).thenReturn(0);

        boolean regenerated = attachmentService.regenerateStudentVersionOrRemoveOutdated(attachment);

        assertThat(regenerated).isFalse();
        verify(fileService, never()).schedulePathForDeletion(any(Path.class), anyLong());
    }

    @Test
    void failedRegenerationWithoutStudentVersionClearsNothing() {
        attachment.setStudentVersion(null);
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of(hiddenSlide()));

        assertThat(attachmentService.regenerateStudentVersionOrRemoveOutdated(attachment)).isFalse();

        verify(attachmentRepository, never()).clearStudentVersionIfUnchanged(anyLong(), anyString());
    }

    private static Slide hiddenSlide() {
        var slide = new Slide();
        slide.setSlideNumber(1);
        slide.setHidden(ZonedDateTime.now().plusDays(1));
        return slide;
    }

    private static Path oldStudentVersionPath() {
        return new FileSystemLocation.StudentVersionSlides(ATTACHMENT_VIDEO_UNIT_ID, OLD_STUDENT_VERSION).path();
    }
}
