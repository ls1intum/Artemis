package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitMetadataWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitVisibilityWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisSlideVisibilityDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;
import de.tum.cit.aet.artemis.videosource.service.ResolvedVideo;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;

class PyrisLectureUnitSyncServiceTest {

    private static final String ARTEMIS_BASE_URL = "https://artemis.example.org";

    private PyrisConnectorService pyrisConnectorService;

    private IrisSettingsService irisSettingsService;

    private VideoSourceResolverService videoSourceResolver;

    private PyrisLectureUnitSyncService service;

    @BeforeEach
    void setUp() {
        pyrisConnectorService = mock(PyrisConnectorService.class);
        irisSettingsService = mock(IrisSettingsService.class);
        videoSourceResolver = mock(VideoSourceResolverService.class);

        service = new PyrisLectureUnitSyncService(pyrisConnectorService, irisSettingsService, videoSourceResolver);
        ReflectionTestUtils.setField(service, "artemisBaseUrl", ARTEMIS_BASE_URL);
    }

    @Test
    void updateLectureUnitMetadataInPyrisSendsLightweightMetadataWithoutReadingAttachmentFile() {
        AttachmentVideoUnit unit = attachmentVideoUnit();
        unit.setVideoSource("https://live.rbg.tum.de/watch/1");
        when(irisSettingsService.isEnabledForCourse(unit.getLecture().getCourse())).thenReturn(true);
        when(videoSourceResolver.resolve(unit.getVideoSource())).thenReturn(new ResolvedVideo("https://cdn.example.org/playlist.m3u8", VideoSourceType.TUM_LIVE, null));

        String token = service.updateLectureUnitMetadataInPyris(unit);

        assertThat(token).isEqualTo("metadata-30");
        ArgumentCaptor<PyrisLectureUnitMetadataWebhookDTO> dtoCaptor = ArgumentCaptor.forClass(PyrisLectureUnitMetadataWebhookDTO.class);
        verify(pyrisConnectorService).executeLectureMetadataWebhook(dtoCaptor.capture());
        PyrisLectureUnitMetadataWebhookDTO dto = dtoCaptor.getValue();
        assertThat(dto.lectureUnitId()).isEqualTo(30L);
        assertThat(dto.lectureUnitName()).isEqualTo("Unit 1");
        // The link the webhook carries is the path the attachment is served under, which is built from the unit and the stored filename without touching the file.
        assertThat(dto.lectureUnitLink()).isEqualTo(ARTEMIS_BASE_URL + "/attachments/attachment-video-units/30/read.pdf");
        assertThat(dto.lectureId()).isEqualTo(20L);
        assertThat(dto.lectureName()).isEqualTo("Lecture 1");
        assertThat(dto.courseId()).isEqualTo(10L);
        assertThat(dto.courseName()).isEqualTo("Course 1");
        assertThat(dto.courseDescription()).isEqualTo("Course description");
        assertThat(dto.videoLink()).isEqualTo("https://cdn.example.org/playlist.m3u8");
        assertThat(dto.baseUrl()).isEqualTo(ARTEMIS_BASE_URL);
    }

    @Test
    void updateLectureUnitMetadataInPyrisKeepsLectureUnitLinkEmptyWhenAttachmentLinkIsMissing() {
        AttachmentVideoUnit unit = attachmentVideoUnit();
        unit.getAttachment().setLink(null);
        when(irisSettingsService.isEnabledForCourse(unit.getLecture().getCourse())).thenReturn(true);
        when(videoSourceResolver.resolve(unit.getVideoSource())).thenReturn(new ResolvedVideo(null, null, null));

        service.updateLectureUnitMetadataInPyris(unit);

        ArgumentCaptor<PyrisLectureUnitMetadataWebhookDTO> dtoCaptor = ArgumentCaptor.forClass(PyrisLectureUnitMetadataWebhookDTO.class);
        verify(pyrisConnectorService).executeLectureMetadataWebhook(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().lectureUnitLink()).isEmpty();
    }

    @Test
    void updateLectureUnitMetadataInPyrisDoesNotResolveVideoForPdfOnlyUnit() {
        AttachmentVideoUnit unit = attachmentVideoUnit();
        unit.setVideoSource(null);
        when(irisSettingsService.isEnabledForCourse(unit.getLecture().getCourse())).thenReturn(true);

        service.updateLectureUnitMetadataInPyris(unit);

        ArgumentCaptor<PyrisLectureUnitMetadataWebhookDTO> dtoCaptor = ArgumentCaptor.forClass(PyrisLectureUnitMetadataWebhookDTO.class);
        verify(pyrisConnectorService).executeLectureMetadataWebhook(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().videoLink()).isNull();
        verify(videoSourceResolver, never()).resolve(any());
    }

    @Test
    void updateLectureUnitVisibilityInPyrisSendsSortedLightweightVisibilityUsingAttachmentReleaseDateFallback() {
        AttachmentVideoUnit unit = attachmentVideoUnit();
        ZonedDateTime releaseDate = ZonedDateTime.parse("2026-07-02T10:15:30+02:00[Europe/Berlin]");
        ZonedDateTime hiddenUntil = ZonedDateTime.parse("2026-07-03T10:15:30+02:00[Europe/Berlin]");
        unit.setReleaseDate(null);
        unit.getAttachment().setReleaseDate(releaseDate);
        unit.getAttachment().setLink("missing/path/that/must/not/be/read.PDF");
        unit.setVideoSource(null);
        when(irisSettingsService.isEnabledForCourse(unit.getLecture().getCourse())).thenReturn(true);

        String token = service.updateLectureUnitVisibilityInPyris(unit, List.of(slide(3, null), slide(1, hiddenUntil), slide(2, null)));

        assertThat(token).isEqualTo("visibility-30");
        ArgumentCaptor<PyrisLectureUnitVisibilityWebhookDTO> dtoCaptor = ArgumentCaptor.forClass(PyrisLectureUnitVisibilityWebhookDTO.class);
        verify(pyrisConnectorService).executeLectureVisibilityWebhook(dtoCaptor.capture());
        PyrisLectureUnitVisibilityWebhookDTO dto = dtoCaptor.getValue();
        assertThat(dto.lectureUnitId()).isEqualTo(30L);
        assertThat(dto.lectureId()).isEqualTo(20L);
        assertThat(dto.courseId()).isEqualTo(10L);
        assertThat(dto.baseUrl()).isEqualTo(ARTEMIS_BASE_URL);
        assertThat(dto.releaseDate().toInstant()).isEqualTo(releaseDate.toInstant());
        assertThat(dto.slides()).extracting(PyrisSlideVisibilityDTO::slideNumber).containsExactly(1, 2, 3);
        assertThat(dto.slides().stream().map(PyrisSlideVisibilityDTO::hiddenUntil).map(value -> value == null ? null : value.toInstant()).toList())
                .containsExactly(hiddenUntil.toInstant(), null, null);
    }

    @Test
    void updateLectureUnitMetadataInPyrisReturnsNullWhenCourseDisabled() {
        AttachmentVideoUnit unit = attachmentVideoUnit();
        when(irisSettingsService.isEnabledForCourse(unit.getLecture().getCourse())).thenReturn(false);

        assertThat(service.updateLectureUnitMetadataInPyris(unit)).isNull();

        verify(pyrisConnectorService, never()).executeLectureMetadataWebhook(any());
        verify(videoSourceResolver, never()).resolve(any());
    }

    private static AttachmentVideoUnit attachmentVideoUnit() {
        Course course = new Course();
        course.setId(10L);
        course.setTitle("Course 1");
        course.setDescription("Course description");

        Lecture lecture = new Lecture();
        lecture.setId(20L);
        lecture.setTitle("Lecture 1");
        lecture.setCourse(course);

        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(30L);
        unit.setName("Unit 1");
        unit.setLecture(lecture);
        unit.setVideoSource("https://youtu.be/dQw4w9WgXcQ");

        Attachment attachment = new Attachment();
        attachment.setAttachmentType(AttachmentType.FILE);
        attachment.setLink("missing/path/that/must/not/be/read.pdf");
        attachment.setAttachmentVideoUnit(unit);
        unit.setAttachment(attachment);
        return unit;
    }

    private static Slide slide(int slideNumber, ZonedDateTime hidden) {
        Slide slide = new Slide();
        slide.setSlideNumber(slideNumber);
        slide.setHidden(hidden);
        return slide;
    }
}
