package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUpdateIntent;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentVideoUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideOrderDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;

// Isolated because setUp repoints the process-wide FilePathConverter upload path at this class's @TempDir.
// Nothing can take that back while other tests are in flight: every integration test resolves upload paths
// through the same static, so one running in parallel wrote into this temp directory and then failed with a
// NoSuchFileException once JUnit deleted it. Running alone keeps the redirected path invisible to everything
// else, and AbstractArtemisIntegrationTest re-asserts the real path for every integration test afterwards.
@Isolated
@ExtendWith(MockitoExtension.class)
class AttachmentVideoUnitServiceTest {

    private static final long LECTURE_UNIT_ID = 42L;

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final String NEW_HASH = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

    @Mock
    private SlideSplitterService slideSplitterService;

    @Mock
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private FileService fileService;

    @Mock
    private LectureUnitService lectureUnitService;

    @Mock
    private LectureContentProcessingService contentProcessingService;

    @Mock
    private AttachmentFileHashService attachmentFileHashService;

    @Mock
    private SlideTestRepository slideRepository;

    @Mock
    private IrisLectureUnitSyncService irisLectureUnitSyncService;

    @Mock
    private AttachmentService attachmentService;

    @TempDir
    private Path tempDir;

    private AttachmentVideoUnitService service;

    private Path originalFileUploadPath;

    @BeforeEach
    void setUp() {
        // Remember what was there: the path is a process-wide static that the integration test base sets once per JVM,
        // so leaving it pointed at this class's @TempDir made every later integration test resolve uploads into a
        // directory JUnit had already deleted.
        originalFileUploadPath = FilePathConverter.getFileUploadPath();
        // Fail here rather than in some later test: with nothing to put back, the restore below would leave this
        // class's @TempDir in the process-wide static, which is exactly the leak this setup exists to avoid.
        assertThat(originalFileUploadPath).as("the file upload path has to be configured before this test can swap it").isNotNull();
        FilePathConverter.setFileUploadPath(tempDir);
        service = new AttachmentVideoUnitService(slideSplitterService, attachmentVideoUnitRepository, attachmentRepository, fileService, Optional.<CompetencyProgressApi>empty(),
                lectureUnitService, Optional.of(contentProcessingService), attachmentFileHashService, new LectureContentUpdateClassifierService(), slideRepository,
                irisLectureUnitSyncService, attachmentService);
        // Lenient: a request rejected before any change never saves the unit or reads its slides.
        lenient().when(attachmentVideoUnitRepository.save(any(AttachmentVideoUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(List.of());
    }

    @AfterEach
    void restoreFileUploadPath() {
        FilePathConverter.setFileUploadPath(originalFileUploadPath);
    }

    @Test
    void updateAttachmentVideoUnitMarksMetadataOnlyChangeDirtyForRetryableSync() {
        var unit = attachmentVideoUnit("Old name", null);
        var dto = attachmentVideoUnitDTO(unit, "New name", unit.getReleaseDate(), unit.getVideoSource());

        service.updateAttachmentVideoUnit(unit, dto, null, null, null, false, null, null, Set.of());

        verify(irisLectureUnitSyncService).markMetadataDirty(any(LectureContentUpdateSnapshot.class));
        verify(irisLectureUnitSyncService, never()).markVisibilityDirty(any());
        verify(contentProcessingService, never()).triggerProcessingForMetadataChange(any());
    }

    @Test
    void saveAttachmentVideoUnitMarksInitialVisibilityDirtyForRetryableSync() {
        var unit = attachmentVideoUnit("New unit", null);

        service.saveAttachmentVideoUnit(unit, null, null, false);

        var snapshotCaptor = ArgumentCaptor.forClass(LectureContentUpdateSnapshot.class);
        verify(irisLectureUnitSyncService).markVisibilityDirty(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().lectureUnitId()).isEqualTo(LECTURE_UNIT_ID);
        assertThat(snapshotCaptor.getValue().releaseDate().toInstant()).isEqualTo(unit.getReleaseDate().toInstant());
        verify(contentProcessingService).triggerProcessing(unit);
    }

    @Test
    void updateAttachmentVideoUnitKeepsSlidesAndStudentVersionForByteIdenticalPdfUploadWithoutVisibility() {
        var attachment = attachment();
        attachment.setStudentVersion("student.pdf");
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.FILE_UPLOAD);
        var uploadedFile = pdfUpload("same content");
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(List.of(slide(21L, 1, null)));

        service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, null, false, null, null, Set.of());

        verify(slideSplitterService, never()).splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class));
        verify(slideSplitterService, never()).updateSlideVisibility(any(), any());
        verify(attachmentService, never()).removeStudentVersionFile(any());
        verify(attachmentService, never()).regenerateStudentVersionOrRemoveOutdated(any());
        assertThat(attachment.getStudentVersion()).endsWith("student.pdf");
        verify(contentProcessingService, never()).triggerProcessing(any());
        verify(irisLectureUnitSyncService, never()).markMetadataDirty(any());
        verify(irisLectureUnitSyncService, never()).markVisibilityDirty(any());
    }

    @Test
    void updateAttachmentVideoUnitAppliesVisibilityForByteIdenticalPdfUpload() {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.FILE_UPLOAD);
        var uploadedFile = pdfUpload("same content");
        ZonedDateTime hiddenUntil = ZonedDateTime.parse("2026-07-10T12:00:00Z");
        var hiddenPages = List.of(new HiddenPageInfoDTO("21", hiddenUntil, null));
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(List.of(slide(21L, 1, null))).thenReturn(List.of(slide(21L, 1, hiddenUntil)));

        service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, null, false, hiddenPages, List.of(new SlideOrderDTO("21", 1)), Set.of());

        verify(slideSplitterService, never()).splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class));
        verify(slideSplitterService).updateSlideVisibility(unit, hiddenPages);
        verify(attachmentService).regenerateStudentVersionOrRemoveOutdated(attachment);
        verify(irisLectureUnitSyncService).markVisibilityDirty(any(LectureContentUpdateSnapshot.class));
        verify(contentProcessingService, never()).triggerProcessing(any());
    }

    @Test
    void updateAttachmentVideoUnitAppliesExplicitlyEmptyVisibilityForByteIdenticalPdfUpload() {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.FILE_UPLOAD);
        var uploadedFile = pdfUpload("same content");
        List<HiddenPageInfoDTO> hiddenPages = List.of();
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(List.of(slide(21L, 1, null)));

        service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, null, false, hiddenPages, null, Set.of());

        verify(slideSplitterService).updateSlideVisibility(unit, hiddenPages);
        verify(attachmentService).regenerateStudentVersionOrRemoveOutdated(attachment);
    }

    @Test
    void updateAttachmentVideoUnitDoesNotFailWhenAsyncSlideSplittingFails() {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.FILE_UPLOAD);
        var uploadedFile = pdfUpload("different content");
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", NEW_HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        when(slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("split failed")));

        assertThatCode(() -> service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, null, false, null, null, Set.of())).doesNotThrowAnyException();

        verify(slideSplitterService).splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class));
    }

    @Test
    void basicReplacementRetiresOldSlidesOfItsOwnRevisionBeforeSplitting() {
        var attachment = attachment();
        attachment.setStudentVersion("student.pdf");
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.FILE_UPLOAD);
        var uploadedFile = pdfUpload("different content");
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", NEW_HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);

        service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, null, false, null, null, Set.of());

        var order = inOrder(attachmentRepository, attachmentService, slideRepository, slideSplitterService);
        order.verify(attachmentRepository).saveAndFlush(attachment);
        order.verify(attachmentService).removeStudentVersionFile(attachment);
        order.verify(slideRepository).supersedeCurrentSlidesIfAttachmentRevisionMatches(LECTURE_UNIT_ID, attachment.getId(), 4, NEW_HASH);
        order.verify(slideSplitterService).splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob.of(unit, null, null));
        verify(attachmentService, never()).regenerateStudentVersionOrRemoveOutdated(any());
    }

    @Test
    void editorReplacementStoresSubmittedStudentVersionWithoutRetiringSlides() throws Exception {
        var attachment = attachment();
        attachment.setStudentVersion("student.pdf");
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.EDITOR_PDF_CONTENT_CHANGED);
        var uploadedFile = pdfUpload("different content");
        var studentVersionFile = new MockMultipartFile("studentVersion", "unit_student.pdf", "application/pdf", "filtered content".getBytes(StandardCharsets.UTF_8));
        var hiddenPages = List.of(new HiddenPageInfoDTO("slide-a", ZonedDateTime.parse("2026-07-04T12:00:00Z"), null));
        var pageOrder = List.of(new SlideOrderDTO("slide-b", 1), new SlideOrderDTO("slide-a", 2));
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", NEW_HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        when(attachmentService.stageUploadedStudentVersionFile(any(), eq(LECTURE_UNIT_ID), eq("unit_student.pdf"))).thenReturn("staged.pdf");

        service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, studentVersionFile, false, hiddenPages, pageOrder, Set.of());

        var order = inOrder(attachmentService, attachmentRepository, slideSplitterService);
        order.verify(attachmentService).stageUploadedStudentVersionFile(any(), eq(LECTURE_UNIT_ID), eq("unit_student.pdf"));
        order.verify(attachmentRepository).saveAndFlush(attachment);
        order.verify(slideSplitterService).splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class));
        order.verify(attachmentService).publishStudentVersionFile(attachment, LECTURE_UNIT_ID, "staged.pdf");
        verify(attachmentService, never()).discardStagedStudentVersionFile(anyLong(), anyString());
        verify(attachmentService, never()).removeStudentVersionFile(any());
        verify(slideRepository, never()).supersedeCurrentSlidesIfAttachmentRevisionMatches(anyLong(), anyLong(), any(), anyString());
        verify(slideSplitterService).splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob.of(unit, hiddenPages, pageOrder));
        verify(attachmentService, never()).regenerateStudentVersionOrRemoveOutdated(any());
    }

    @Test
    void metadataOnlyUpdateKeepsStudentVersion() {
        var attachment = attachment();
        attachment.setStudentVersion("student.pdf");
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.NO_FILE_CHANGE);
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);

        service.updateAttachmentVideoUnit(unit, dto, attachment, null, null, false, null, null, Set.of());

        assertThat(attachment.getStudentVersion()).endsWith("student.pdf");
        verify(slideSplitterService, never()).updateSlideVisibility(any(), any());
        verify(attachmentService, never()).removeStudentVersionFile(any());
        verify(attachmentService, never()).regenerateStudentVersionOrRemoveOutdated(any());
    }

    @Test
    void updateAttachmentVideoUnitLeavesIrisVisibilityToTheScheduledSplit() {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var existingSlide = new Slide();
        existingSlide.setId(21L);
        existingSlide.setSlideNumber(1);
        existingSlide.setHidden(null);
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(List.of(existingSlide));
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.FILE_UPLOAD);
        var uploadedFile = pdfUpload("different content");
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", NEW_HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        ZonedDateTime hiddenUntil = ZonedDateTime.parse("2026-07-10T12:00:00Z");

        service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, null, false, List.of(new HiddenPageInfoDTO("21", hiddenUntil, null)),
                List.of(new SlideOrderDTO("21", 1)), Set.of());

        // The split tells Iris about the slides it saved once it is done; a projection sent here could arrive after that and overwrite it.
        verify(slideSplitterService).splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class));
        verify(irisLectureUnitSyncService, never()).markVisibilityDirty(any());
        verify(contentProcessingService).triggerProcessing(unit);
    }

    @Test
    void identicalUploadRebuildsAMissingDeck() {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.FILE_UPLOAD);
        var uploadedFile = pdfUpload("same content");
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);

        service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, null, false, null, null, Set.of());

        verify(slideSplitterService).splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob.of(unit, null, null));
        verify(attachmentService, never()).removeStudentVersionFile(any());
    }

    @Test
    void invalidStudentVersionFailsBeforeAnythingChanges() throws Exception {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.EDITOR_PDF_CONTENT_CHANGED);
        var studentVersionFile = new MockMultipartFile("studentVersion", "unit_student.exe", "application/octet-stream", "content".getBytes(StandardCharsets.UTF_8));
        when(attachmentService.stageUploadedStudentVersionFile(any(), eq(LECTURE_UNIT_ID), eq("unit_student.exe"))).thenThrow(new IllegalArgumentException("bad extension"));

        assertThatCode(() -> service.updateAttachmentVideoUnit(unit, dto, attachment, pdfUpload("different content"), studentVersionFile, false, List.of(),
                List.of(new SlideOrderDTO("21", 1)), Set.of())).isInstanceOf(IllegalArgumentException.class);

        verify(attachmentVideoUnitRepository, never()).save(any(AttachmentVideoUnit.class));
        verify(attachmentRepository, never()).saveAndFlush(any());
        verify(slideSplitterService, never()).splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class));
    }

    @Test
    void failureAfterStagingDiscardsTheStagedStudentVersion() throws Exception {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.EDITOR_PDF_CONTENT_CHANGED);
        var uploadedFile = pdfUpload("different content");
        var studentVersionFile = new MockMultipartFile("studentVersion", "unit_student.pdf", "application/pdf", "content".getBytes(StandardCharsets.UTF_8));
        when(attachmentService.stageUploadedStudentVersionFile(any(), eq(LECTURE_UNIT_ID), eq("unit_student.pdf"))).thenReturn("staged.pdf");
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", NEW_HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(
                () -> service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, studentVersionFile, false, List.of(), List.of(new SlideOrderDTO("21", 1)), Set.of()))
                .isInstanceOf(IllegalStateException.class);

        verify(attachmentService).discardStagedStudentVersionFile(LECTURE_UNIT_ID, "staged.pdf");
        verify(attachmentService, never()).publishStudentVersionFile(any(), anyLong(), anyString());
    }

    @Test
    void failedPublicationRemovesTheOutdatedStudentVersionAfterTheSlideWorkIsDone() throws Exception {
        var attachment = attachment();
        attachment.setStudentVersion("student.pdf");
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.EDITOR_PDF_CONTENT_CHANGED);
        var uploadedFile = pdfUpload("different content");
        var studentVersionFile = new MockMultipartFile("studentVersion", "unit_student.pdf", "application/pdf", "content".getBytes(StandardCharsets.UTF_8));
        var pageOrder = List.of(new SlideOrderDTO("21", 1));
        when(attachmentService.stageUploadedStudentVersionFile(any(), eq(LECTURE_UNIT_ID), eq("unit_student.pdf"))).thenReturn("staged.pdf");
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", NEW_HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        doThrow(new IllegalStateException("database unavailable")).when(attachmentService).publishStudentVersionFile(attachment, LECTURE_UNIT_ID, "staged.pdf");

        assertThatCode(() -> service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, studentVersionFile, false, List.of(), pageOrder, Set.of()))
                .isInstanceOf(IllegalStateException.class);

        verify(slideSplitterService).splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class));
        verify(contentProcessingService).triggerProcessing(unit);
        verify(attachmentService).removeStudentVersionFile(attachment);
        verify(attachmentService, never()).discardStagedStudentVersionFile(anyLong(), anyString());
    }

    @Test
    void updateAttachmentVideoUnitPersistsVisibilityWithoutReprocessingContent() {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var existingSlide = new Slide();
        existingSlide.setId(21L);
        existingSlide.setSlideNumber(1);
        existingSlide.setHidden(null);
        ZonedDateTime hiddenUntil = ZonedDateTime.parse("2026-07-10T12:00:00Z");
        var hiddenPages = List.of(new HiddenPageInfoDTO("21", hiddenUntil, null));
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.NO_FILE_CHANGE);
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(List.of(existingSlide)).thenReturn(List.of(slide(21L, 1, hiddenUntil)));

        service.updateAttachmentVideoUnit(unit, dto, attachment, null, null, false, hiddenPages, null, Set.of());

        var order = inOrder(slideSplitterService, irisLectureUnitSyncService, attachmentService);
        order.verify(slideSplitterService).updateSlideVisibility(unit, hiddenPages);
        order.verify(irisLectureUnitSyncService).markVisibilityDirty(any(LectureContentUpdateSnapshot.class));
        order.verify(attachmentService).regenerateStudentVersionOrRemoveOutdated(attachment);
        verify(contentProcessingService, never()).triggerProcessing(any());
    }

    @Test
    void updateAttachmentVideoUnitTriggersAsyncContentProcessingForVideoSourceChange() {
        var unit = attachmentVideoUnit("Unit", null);
        var dto = attachmentVideoUnitDTO(unit, unit.getName(), unit.getReleaseDate(), "https://video.example/updated");

        service.updateAttachmentVideoUnit(unit, dto, null, null, null, false, null, null, Set.of());

        verify(contentProcessingService).triggerProcessing(unit);
        verify(irisLectureUnitSyncService, never()).markMetadataDirty(any());
        verify(irisLectureUnitSyncService, never()).markVisibilityDirty(any());
    }

    @Test
    void updateAttachmentVideoUnitMarksMetadataDirtyWhenContentProcessingIsUnavailable() {
        service = new AttachmentVideoUnitService(slideSplitterService, attachmentVideoUnitRepository, attachmentRepository, fileService, Optional.empty(), lectureUnitService,
                Optional.empty(), attachmentFileHashService, new LectureContentUpdateClassifierService(), slideRepository, irisLectureUnitSyncService, attachmentService);
        var unit = attachmentVideoUnit("Old name", null);
        var dto = attachmentVideoUnitDTO(unit, "New name", unit.getReleaseDate(), "https://video.example/updated");

        service.updateAttachmentVideoUnit(unit, dto, null, null, null, false, null, null, Set.of());

        verify(irisLectureUnitSyncService).markMetadataDirty(any(LectureContentUpdateSnapshot.class));
    }

    @Test
    void updateAttachmentVideoUnitMarksMetadataAndVisibilityDirtyWhenBothChange() {
        var unit = attachmentVideoUnit("Old name", null);
        var updatedReleaseDate = unit.getReleaseDate().plusDays(1);
        var dto = attachmentVideoUnitDTO(unit, "New name", updatedReleaseDate, unit.getVideoSource());

        service.updateAttachmentVideoUnit(unit, dto, null, null, null, false, null, null, Set.of());

        verify(irisLectureUnitSyncService).markMetadataDirty(any(LectureContentUpdateSnapshot.class));
        verify(irisLectureUnitSyncService).markVisibilityDirty(any(LectureContentUpdateSnapshot.class));
        verify(contentProcessingService, never()).triggerProcessingForMetadataChange(any());
    }

    private static AttachmentVideoUnit attachmentVideoUnit(String name, Attachment attachment) {
        var course = new Course();
        course.setTitle("Course");
        course.setDescription("Course description");

        var lecture = new Lecture();
        lecture.setId(7L);
        lecture.setTitle("Lecture");
        lecture.setCourse(course);

        var unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        unit.setName(name);
        unit.setDescription("Description");
        unit.setReleaseDate(ZonedDateTime.parse("2026-07-02T12:00:00Z"));
        unit.setVideoSource("https://video.example/source");
        lecture.addLectureUnit(unit);

        if (attachment != null) {
            attachment.setAttachmentVideoUnit(unit);
            unit.setAttachment(attachment);
        }
        return unit;
    }

    private static AttachmentVideoUnitDTO attachmentVideoUnitDTO(AttachmentVideoUnit unit, String name, ZonedDateTime releaseDate, String videoSource) {
        var current = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.NO_FILE_CHANGE);
        return new AttachmentVideoUnitDTO(current.id(), name, releaseDate, current.description(), videoSource, current.competencyLinks(), current.attachment(), current.slides(),
                current.completed(), current.visibleToStudents(), current.lecture(), current.attachmentUpdateIntent(), current.type());
    }

    private static Attachment attachment() {
        var attachment = new Attachment();
        attachment.setId(11L);
        attachment.setName("Unit PDF");
        attachment.setVersion(3);
        attachment.setLink("attachments/attachment-unit/" + LECTURE_UNIT_ID + "/unit.pdf");
        attachment.setSha256Hash(HASH);
        return attachment;
    }

    private static MockMultipartFile pdfUpload(String content) {
        return new MockMultipartFile("file", "unit.pdf", "application/pdf", content.getBytes(StandardCharsets.UTF_8));
    }

    private static Slide slide(long id, int slideNumber, ZonedDateTime hidden) {
        var slide = new Slide();
        slide.setId(id);
        slide.setSlideNumber(slideNumber);
        slide.setHidden(hidden);
        return slide;
    }
}
