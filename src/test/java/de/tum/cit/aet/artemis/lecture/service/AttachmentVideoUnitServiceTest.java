package de.tum.cit.aet.artemis.lecture.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUpdateIntent;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentVideoUnitDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@ExtendWith(MockitoExtension.class)
class AttachmentVideoUnitServiceTest {

    private static final long LECTURE_UNIT_ID = 42L;

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private SlideSplitterService slideSplitterService;

    @Mock
    private AttachmentVideoUnitRepository attachmentVideoUnitRepository;

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
    private AttachmentService attachmentService;

    @Mock
    private SlideRepository slideRepository;

    private AttachmentVideoUnitService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentVideoUnitService(slideSplitterService, attachmentVideoUnitRepository, attachmentRepository, fileService, Optional.<CompetencyProgressApi>empty(),
                lectureUnitService, Optional.of(contentProcessingService), attachmentFileHashService, attachmentService, new LectureContentUpdateClassifier(), slideRepository);
        when(attachmentVideoUnitRepository.save(any(AttachmentVideoUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(List.of());
    }

    @Test
    void updateAttachmentVideoUnitRoutesMetadataOnlyChangeThroughUpdateKindDispatcher() {
        var unit = attachmentVideoUnit("Old name", null);
        var dto = new AttachmentVideoUnitDTO(LECTURE_UNIT_ID, "New name", unit.getReleaseDate(), unit.getDescription(), unit.getVideoSource(), null,
                AttachmentUpdateIntent.NO_FILE_CHANGE);

        service.updateAttachmentVideoUnit(unit, dto, null, null, false, null, null, Set.of());

        verify(contentProcessingService).triggerProcessingForUpdateKind(unit, LectureContentUpdateKind.METADATA);
        verify(contentProcessingService, never()).triggerProcessingForMetadataChange(any());
    }

    @Test
    void updateAttachmentVideoUnitDoesNotSplitSlidesForByteIdenticalUpload() {
        var attachment = attachment();
        var unit = attachmentVideoUnit("Unit", attachment);
        var dto = AttachmentVideoUnitDTO.from(unit, AttachmentUpdateIntent.FILE_UPLOAD);
        var uploadedFile = mock(MultipartFile.class);
        when(uploadedFile.isEmpty()).thenReturn(false);
        when(attachmentFileHashService.sha256(uploadedFile)).thenReturn(new AttachmentFileHashService.FileHash("SHA-256", HASH));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);

        service.updateAttachmentVideoUnit(unit, dto, attachment, uploadedFile, false, null, null, Set.of());

        verify(slideSplitterService, never()).splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnit.class));
        verify(slideSplitterService, never()).splitAttachmentVideoUnitIntoSingleSlides(any(AttachmentVideoUnit.class), any(), any());
        verify(contentProcessingService).triggerProcessingForUpdateKind(unit, LectureContentUpdateKind.NONE);
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

    private static Attachment attachment() {
        var attachment = new Attachment();
        attachment.setId(11L);
        attachment.setName("Unit PDF");
        attachment.setVersion(3);
        attachment.setLink("attachments/attachment-unit/" + LECTURE_UNIT_ID + "/unit.pdf");
        attachment.setSha256Hash(HASH);
        return attachment;
    }
}
