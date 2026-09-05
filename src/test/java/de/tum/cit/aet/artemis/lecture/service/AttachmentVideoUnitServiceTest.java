package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.web.multipart.MultipartFile;

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
                irisLectureUnitSyncService);
        when(attachmentVideoUnitRepository.save(any(AttachmentVideoUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(List.of());
    }

    @AfterEach
    void restoreFileUploadPath() {
        FilePathConverter.setFileUploadPath(originalFileUploadPath);
    }

    @Test
    void updateAttachmentVideoUnitMarksMetadataOnlyChangeDirtyForRetryableSync() {
        var unit = attachmentVideoUnit("Old name", null);
        var dto = attachmentVideoUnitDTO(unit, "New name", unit.getReleaseDate(), unit.getVideoSource());

        service.updateAttachmentVideoUnit(unit, dto, null, null, false, null, null, Set.of());

        verify(irisLectureUnitSyncService).markMetadataDirtyAfterCommit(any(LectureContentUpdateSnapshot.class));
        verify(irisLectureUnitSyncService, never()).markVisibilityDirtyAfterCommit(any());
        verify(contentProcessingService, never()).triggerProcessingForMetadataChange(any());
    }

    @Test
    void saveAttachmentVideoUnitMarksInitialVisibilityDirtyForRetryableSync() {
        var unit = attachmentVideoUnit("New unit", null);

        service.saveAttachmentVideoUnit(unit, null, null, false);

        var snapshotCaptor = ArgumentCaptor.forClass(LectureContentUpdateSnapshot.class);
        verify(irisLectureUnitSyncService).markVisibilityDirtyAfterCommit(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().lectureUnitId()).isEqualTo(LECTURE_UNIT_ID);
        assertThat(snapshotCaptor.getValue().releaseDate().toInstant()).isEqualTo(unit.getReleaseDate().toInstant());
        verify(contentProcessingService).triggerProcessing(unit);
    }

    @Test
    void updateAttachmentVideoUnitSplitsSlidesForByteIdenticalPdfUpload() {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.FILE_UPLOAD);
        var uploadedFile = mock(MultipartFile.class);
        when(uploadedFile.isEmpty()).thenReturn(false);
        when(uploadedFile.getOriginalFilename()).thenReturn("lecture.pdf");
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        when(slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class))).thenReturn(CompletableFuture.completedFuture(null));

        service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, false, null, null, Set.of());

        verify(slideSplitterService).splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class));
        verify(contentProcessingService, never()).triggerProcessing(any());
        verify(irisLectureUnitSyncService, never()).markMetadataDirtyAfterCommit(any());
        verify(irisLectureUnitSyncService, never()).markVisibilityDirtyAfterCommit(any());
        verify(contentProcessingService, never()).triggerProcessingForMetadataChange(any());
    }

    @Test
    void updateAttachmentVideoUnitDoesNotFailWhenAsyncSlideSplittingFails() {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.FILE_UPLOAD);
        var uploadedFile = mock(MultipartFile.class);
        when(uploadedFile.isEmpty()).thenReturn(false);
        when(uploadedFile.getOriginalFilename()).thenReturn("lecture.pdf");
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        when(slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("split failed")));

        assertThatCode(() -> service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, false, null, null, Set.of())).doesNotThrowAnyException();

        verify(slideSplitterService).splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnitSlideSplitJob.class));
        verify(contentProcessingService, never()).triggerProcessing(any());
        verify(irisLectureUnitSyncService, never()).markVisibilityDirtyAfterCommit(any());
    }

    @Test
    void updateAttachmentVideoUnitUsesSubmittedHiddenPagesForVisibilityClassification() {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var existingSlide = new Slide();
        existingSlide.setId(21L);
        existingSlide.setSlideNumber(1);
        existingSlide.setHidden(null);
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(List.of(existingSlide));
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.FILE_UPLOAD);
        var uploadedFile = mock(MultipartFile.class);
        when(uploadedFile.isEmpty()).thenReturn(false);
        when(uploadedFile.getOriginalFilename()).thenReturn("lecture.pdf");
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        ZonedDateTime hiddenUntil = ZonedDateTime.parse("2026-07-10T12:00:00Z");

        service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, false, List.of(new HiddenPageInfoDTO("21", hiddenUntil, null)), List.of(new SlideOrderDTO("21", 1)),
                Set.of());

        var snapshotCaptor = ArgumentCaptor.forClass(LectureContentUpdateSnapshot.class);
        verify(irisLectureUnitSyncService).markVisibilityDirtyAfterCommit(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().slideHiddenUntilBySlideNumber()).containsOnlyKeys(1);
        assertThat(snapshotCaptor.getValue().slideHiddenUntilBySlideNumber().get(1).toInstant()).isEqualTo(hiddenUntil.toInstant());
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

        service.updateAttachmentVideoUnit(unit, dto, attachment, null, false, hiddenPages, null, Set.of());

        verify(slideSplitterService).updateSlideVisibility(unit, hiddenPages);
        verify(contentProcessingService, never()).triggerProcessing(any());
        verify(irisLectureUnitSyncService).markVisibilityDirtyAfterCommit(any(LectureContentUpdateSnapshot.class));
    }

    @Test
    void updateAttachmentVideoUnitTriggersAsyncContentProcessingForVideoSourceChange() {
        var unit = attachmentVideoUnit("Unit", null);
        var dto = attachmentVideoUnitDTO(unit, unit.getName(), unit.getReleaseDate(), "https://video.example/updated");

        service.updateAttachmentVideoUnit(unit, dto, null, null, false, null, null, Set.of());

        verify(contentProcessingService).triggerProcessing(unit);
        verify(irisLectureUnitSyncService, never()).markMetadataDirtyAfterCommit(any());
        verify(irisLectureUnitSyncService, never()).markVisibilityDirtyAfterCommit(any());
    }

    @Test
    void updateAttachmentVideoUnitMarksMetadataDirtyWhenContentProcessingIsUnavailable() {
        service = new AttachmentVideoUnitService(slideSplitterService, attachmentVideoUnitRepository, attachmentRepository, fileService, Optional.empty(), lectureUnitService,
                Optional.empty(), attachmentFileHashService, new LectureContentUpdateClassifierService(), slideRepository, irisLectureUnitSyncService);
        var unit = attachmentVideoUnit("Old name", null);
        var dto = attachmentVideoUnitDTO(unit, "New name", unit.getReleaseDate(), "https://video.example/updated");

        service.updateAttachmentVideoUnit(unit, dto, null, null, false, null, null, Set.of());

        verify(irisLectureUnitSyncService).markMetadataDirtyAfterCommit(any(LectureContentUpdateSnapshot.class));
    }

    @Test
    void updateAttachmentVideoUnitMarksMetadataAndVisibilityDirtyWhenBothChange() {
        var unit = attachmentVideoUnit("Old name", null);
        var updatedReleaseDate = unit.getReleaseDate().plusDays(1);
        var dto = attachmentVideoUnitDTO(unit, "New name", updatedReleaseDate, unit.getVideoSource());

        service.updateAttachmentVideoUnit(unit, dto, null, null, false, null, null, Set.of());

        verify(irisLectureUnitSyncService).markMetadataDirtyAfterCommit(any(LectureContentUpdateSnapshot.class));
        verify(irisLectureUnitSyncService).markVisibilityDirtyAfterCommit(any(LectureContentUpdateSnapshot.class));
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

    private static Slide slide(long id, int slideNumber, ZonedDateTime hidden) {
        var slide = new Slide();
        slide.setId(id);
        slide.setSlideNumber(slideNumber);
        slide.setHidden(hidden);
        return slide;
    }
}
