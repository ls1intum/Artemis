package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentStudentVersionStateDTO;
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
        attachment.setVersion(1);
        // Lenient: the tests that never regenerate do not read it.
        lenient().when(attachmentRepository.findStudentVersionStateById(ATTACHMENT_ID)).thenReturn(Optional.of(new AttachmentStudentVersionStateDTO(OLD_STUDENT_VERSION, 1)));
    }

    @Test
    void uploadedStudentVersionStoresReferenceBeforeDeletingOldFile() throws IOException {
        when(attachmentRepository.updateStudentVersionIfFileUnchanged(eq(ATTACHMENT_ID), anyString(), eq(1))).thenReturn(1);

        attachmentService.replaceUploadedStudentVersionFile(new byte[] { 1, 2, 3 }, attachment, ATTACHMENT_VIDEO_UNIT_ID, "student.pdf");

        var installedPath = ArgumentCaptor.forClass(Path.class);
        var order = inOrder(tempFileUtilService, attachmentRepository, fileService);
        order.verify(tempFileUtilService).replaceFileAtomically(any(Path.class), installedPath.capture(), any(byte[].class));
        order.verify(attachmentRepository).updateStudentVersionIfFileUnchanged(eq(ATTACHMENT_ID), anyString(), eq(1));
        order.verify(fileService).schedulePathForDeletion(oldStudentVersionPath(), 0);

        String newFilename = installedPath.getValue().getFileName().toString();
        assertThat(newFilename).isNotEqualTo(OLD_STUDENT_VERSION).endsWith(".pdf");
        verify(attachmentRepository).updateStudentVersionIfFileUnchanged(ATTACHMENT_ID, newFilename, 1);
        assertThat(FileSystemLocation.storedFilename(attachment.getStudentVersion())).isEqualTo(newFilename);
    }

    @Test
    void failureToStoreReferenceDeletesNewFileAndKeepsOldOne() throws IOException {
        doThrow(new IllegalStateException("database unavailable")).when(attachmentRepository).updateStudentVersionIfFileUnchanged(eq(ATTACHMENT_ID), anyString(), eq(1));

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
        when(attachmentRepository.findStudentVersionStateById(ATTACHMENT_ID)).thenReturn(Optional.of(new AttachmentStudentVersionStateDTO(null, 1)));
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of(hiddenSlide()));

        assertThat(attachmentService.regenerateStudentVersionOrRemoveOutdated(attachment)).isFalse();

        verify(attachmentRepository, never()).clearStudentVersionIfUnchanged(anyLong(), anyString());
    }

    @Test
    void uploadedStudentVersionMustBeAPdf() throws IOException {
        assertThatThrownBy(() -> attachmentService.stageUploadedStudentVersionFile(new byte[] { 1 }, ATTACHMENT_VIDEO_UNIT_ID, "student.exe"))
                .isInstanceOf(BadRequestAlertException.class);

        verify(tempFileUtilService, never()).replaceFileAtomically(any(Path.class), any(Path.class), any(byte[].class));
    }

    @Test
    void uploadedStudentVersionNameDoesNotUseTheUploadedName() throws IOException {
        String filename = attachmentService.stageUploadedStudentVersionFile(new byte[] { 1 }, ATTACHMENT_VIDEO_UNIT_ID, "lecture notes final.PDF");

        assertThat(filename).startsWith("StudentVersionSlides_").endsWith(".pdf").doesNotContain("lecture");
    }

    @Test
    void storedValueOutsideTheStudentVersionDirectoryIsNeverDeleted() {
        attachment.setStudentVersion("..");
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of());
        when(attachmentRepository.clearStudentVersionIfUnchanged(ATTACHMENT_ID, "..")).thenReturn(1);

        attachmentService.regenerateStudentVersion(attachment);

        verify(fileService, never()).schedulePathForDeletion(any(Path.class), anyLong());
    }

    @Test
    void uploadedStudentVersionIsDiscardedWhenANewerFileReplacedItsFile() throws IOException {
        // Another save stored a newer file and its own student version before this upload's student version was published.
        when(attachmentRepository.updateStudentVersionIfFileUnchanged(eq(ATTACHMENT_ID), anyString(), eq(1))).thenReturn(0);

        attachmentService.replaceUploadedStudentVersionFile(new byte[] { 1, 2, 3 }, attachment, ATTACHMENT_VIDEO_UNIT_ID, "student.pdf");

        var installedPath = ArgumentCaptor.forClass(Path.class);
        verify(tempFileUtilService).replaceFileAtomically(any(Path.class), installedPath.capture(), any(byte[].class));
        verify(fileService).schedulePathForDeletion(installedPath.getValue(), 0);
        verify(fileService, never()).schedulePathForDeletion(oldStudentVersionPath(), 0);
        assertThat(FileSystemLocation.storedFilename(attachment.getStudentVersion())).isEqualTo(OLD_STUDENT_VERSION);
    }

    @Test
    void failedRegenerationRemovesTheReferenceItReadNotTheCallersOlderOne() {
        // The caller loaded the attachment while it pointed at old.pdf; a regeneration in between published fresh.pdf. This regeneration reads fresh.pdf and then fails
        // (the stored file does not exist), so fresh.pdf no longer matches the slides and must stop being served.
        when(attachmentRepository.findStudentVersionStateById(ATTACHMENT_ID)).thenReturn(Optional.of(new AttachmentStudentVersionStateDTO("fresh.pdf", 1)));
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of(hiddenSlide()));
        when(attachmentRepository.clearStudentVersionIfUnchanged(ATTACHMENT_ID, "fresh.pdf")).thenReturn(1);

        assertThat(attachmentService.regenerateStudentVersionOrRemoveOutdated(attachment)).isFalse();

        verify(attachmentRepository).clearStudentVersionIfUnchanged(ATTACHMENT_ID, "fresh.pdf");
        verify(attachmentRepository, never()).clearStudentVersionIfUnchanged(ATTACHMENT_ID, OLD_STUDENT_VERSION);
    }

    @Test
    void regenerationStartsOverWhenTheAttachmentChangedBeforeItsPdfIsPublished() throws IOException {
        var spiedService = spy(attachmentService);
        List<Slide> firstState = List.of(slide(1L, 1));
        List<Slide> laterState = List.of(slide(1L, 1), slide(2L, 2));
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(firstState, laterState);
        doReturn(new byte[] { 1 }).when(spiedService).generateStudentVersionPdf(any(File.class), anyList());
        // An overlapping update hid slide 2 before the first PDF was published, so publishing it matches nothing.
        when(attachmentRepository.publishStudentVersionIfUnchanged(eq(ATTACHMENT_ID), eq(ATTACHMENT_VIDEO_UNIT_ID), anyString(), eq(1), eq(OLD_STUDENT_VERSION), eq(List.of(1L)),
                eq(1L))).thenReturn(0);
        when(attachmentRepository.publishStudentVersionIfUnchanged(eq(ATTACHMENT_ID), eq(ATTACHMENT_VIDEO_UNIT_ID), anyString(), eq(1), eq(OLD_STUDENT_VERSION),
                eq(List.of(1L, 2L)), eq(2L))).thenReturn(1);

        spiedService.regenerateStudentVersion(attachment);

        var writtenPaths = ArgumentCaptor.forClass(Path.class);
        verify(tempFileUtilService, times(2)).replaceFileAtomically(any(Path.class), writtenPaths.capture(), any(byte[].class));
        Path discarded = writtenPaths.getAllValues().getFirst();
        Path published = writtenPaths.getAllValues().getLast();
        verify(spiedService).generateStudentVersionPdf(any(File.class), eq(laterState));
        verify(fileService).schedulePathForDeletion(discarded, 0);
        verify(fileService, never()).schedulePathForDeletion(published, 0);
        verify(fileService).schedulePathForDeletion(oldStudentVersionPath(), 0);
        assertThat(FileSystemLocation.storedFilename(attachment.getStudentVersion())).isEqualTo(published.getFileName().toString());
    }

    @Test
    void regenerationFailsClosedWhenTheAttachmentKeepsChanging() throws IOException {
        var spiedService = spy(attachmentService);
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of(slide(1L, 1)));
        doReturn(new byte[] { 1 }).when(spiedService).generateStudentVersionPdf(any(File.class), anyList());
        when(attachmentRepository.publishStudentVersionIfUnchanged(anyLong(), anyLong(), anyString(), any(), any(), anyList(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> spiedService.regenerateStudentVersion(attachment)).isInstanceOf(InternalServerErrorException.class);

        verify(spiedService, times(3)).generateStudentVersionPdf(any(File.class), anyList());
        verify(fileService, times(3)).schedulePathForDeletion(any(Path.class), eq(0L));
        verify(fileService, never()).schedulePathForDeletion(oldStudentVersionPath(), 0);
        assertThat(FileSystemLocation.storedFilename(attachment.getStudentVersion())).isEqualTo(OLD_STUDENT_VERSION);
    }

    @Test
    void regenerationStopsWhenTheFileWasReplacedInTheMeantime() throws IOException {
        var spiedService = spy(attachmentService);
        when(slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(ATTACHMENT_VIDEO_UNIT_ID)).thenReturn(List.of(slide(1L, 1)));
        doReturn(new byte[] { 1 }).when(spiedService).generateStudentVersionPdf(any(File.class), anyList());
        // An editor stored a new file with its own student version before the first PDF was published.
        when(attachmentRepository.findStudentVersionStateById(ATTACHMENT_ID)).thenReturn(Optional.of(new AttachmentStudentVersionStateDTO(OLD_STUDENT_VERSION, 1)),
                Optional.of(new AttachmentStudentVersionStateDTO("editor.pdf", 2)));
        when(attachmentRepository.publishStudentVersionIfUnchanged(anyLong(), anyLong(), anyString(), any(), any(), anyList(), anyLong())).thenReturn(0);

        spiedService.regenerateStudentVersion(attachment);

        verify(spiedService, times(1)).generateStudentVersionPdf(any(File.class), anyList());
        verify(attachmentRepository, times(1)).publishStudentVersionIfUnchanged(anyLong(), anyLong(), anyString(), any(), any(), anyList(), anyLong());
    }

    @Test
    void missingStudentVersionIsNotGeneratedOnceOneWasStoredInTheMeantime() throws IOException {
        var spiedService = spy(attachmentService);

        spiedService.generateMissingStudentVersion(attachment);

        verify(spiedService, never()).generateStudentVersionPdf(any(File.class), anyList());
        verify(attachmentRepository, never()).publishStudentVersionIfUnchanged(anyLong(), anyLong(), anyString(), any(), any(), anyList(), anyLong());
    }

    private static Slide slide(long id, int slideNumber) {
        var slide = new Slide();
        slide.setId(id);
        slide.setSlideNumber(slideNumber);
        slide.setHidden(ZonedDateTime.now().plusDays(1));
        return slide;
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
