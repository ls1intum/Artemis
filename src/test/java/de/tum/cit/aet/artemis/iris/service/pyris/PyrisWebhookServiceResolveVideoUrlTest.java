package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.exception.IrisVideoSourceUnavailableException;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.videosource.api.TumLiveApi;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;
import de.tum.cit.aet.artemis.videosource.service.TumLivePlaylistLookup;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;
import de.tum.cit.aet.artemis.videosource.service.YouTubeUrlService;

class PyrisWebhookServiceResolveVideoUrlTest {

    private final YouTubeUrlService youTubeUrlService = new YouTubeUrlService();

    private PyrisWebhookService withTumLive(TumLiveApi tumLiveApi) {
        VideoSourceResolverService resolver = new VideoSourceResolverService(Optional.ofNullable(tumLiveApi), Optional.empty(), youTubeUrlService);
        return new PyrisWebhookService(mock(PyrisConnectorService.class), mock(PyrisJobService.class), mock(PyrisCallbackTokenService.class), mock(IrisSettingsService.class),
                Optional.empty(), Optional.empty(), resolver);
    }

    @Test
    void tumLiveMatchReturnsTumLiveType() {
        var api = mock(TumLiveApi.class);
        when(api.lookUpPlaylist(any())).thenReturn(new TumLivePlaylistLookup("https://live.rbg.tum.de/pl.m3u8", TumLivePlaylistLookup.Outcome.RESOLVED));
        var svc = withTumLive(api);
        var resolved = svc.resolveVideoUrl("https://live.rbg.tum.de/?course=foo&streamId=1", 1L);
        assertThat(resolved.url()).isEqualTo("https://live.rbg.tum.de/pl.m3u8");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
    }

    @Test
    void youTubeUrlReturnsYouTubeType() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("https://youtu.be/dQw4w9WgXcQ", 1L);
        assertThat(resolved.url()).isEqualTo("https://youtu.be/dQw4w9WgXcQ");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }

    @Test
    void unknownSourceReturnsNullType() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("https://vimeo.com/123", 1L);
        assertThat(resolved.url()).isEqualTo("https://vimeo.com/123");
        assertThat(resolved.type()).isNull();
    }

    @Test
    void nullUrlReturnsNullResolution() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl(null, 1L);
        assertThat(resolved.url()).isNull();
        assertThat(resolved.type()).isNull();
    }

    @Test
    void blankUrlReturnsBlankResolution() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("   ", 1L);
        assertThat(resolved.url()).isEqualTo("   ");
        assertThat(resolved.type()).isNull();
    }

    @Test
    void tumLiveApiExceptionFallsBackToOriginalUrlWithNullType() {
        var api = mock(TumLiveApi.class);
        when(api.lookUpPlaylist(any())).thenThrow(new RuntimeException("boom"));
        var svc = withTumLive(api);
        var resolved = svc.resolveVideoUrl("https://live.rbg.tum.de/?x=1", 1L);
        assertThat(resolved.url()).isEqualTo("https://live.rbg.tum.de/?x=1");
        assertThat(resolved.type()).isNull();
    }

    @Test
    void tumLiveCheckedBeforeYouTubeSoTumLiveWins() {
        var api = mock(TumLiveApi.class);
        when(api.lookUpPlaylist(any())).thenReturn(new TumLivePlaylistLookup("https://live.rbg.tum.de/resolved.m3u8", TumLivePlaylistLookup.Outcome.RESOLVED));
        var svc = withTumLive(api);
        var resolved = svc.resolveVideoUrl("https://live.rbg.tum.de/foo", 1L);
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
    }

    @Test
    void absentTumLiveApiYouTubeStillResolves() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ", 1L);
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }

    private static final String TUM_LIVE_VIDEO = "https://live.rbg.tum.de/w/course/1";

    private static final String FULL_FINGERPRINT = "v1:full";

    private static final String FINGERPRINT_WITHOUT_VIDEO = "v1:without-video";

    @TempDir
    private Path tempDir;

    private final PyrisJobService jobService = mock(PyrisJobService.class);

    private final LectureTranscriptionsRepositoryApi transcriptions = mock(LectureTranscriptionsRepositoryApi.class);

    /** A service whose TUM Live resolves the test video with the given outcome. */
    private PyrisWebhookService withTumLiveOutcome(TumLivePlaylistLookup lookup) {
        var api = mock(TumLiveApi.class);
        when(api.isTumLiveUrl(TUM_LIVE_VIDEO)).thenReturn(true);
        when(api.lookUpPlaylist(TUM_LIVE_VIDEO)).thenReturn(lookup);
        IrisSettingsService settings = mock(IrisSettingsService.class);
        when(settings.getSettingsForCourse(any(Course.class))).thenReturn(IrisCourseSettings.defaultSettings());
        when(jobService.addLectureIngestionWebhookJob(anyLong(), anyLong(), anyLong())).thenReturn("job-token");
        when(jobService.getJob("job-token")).thenReturn(new LectureIngestionWebhookJob("job-token", 1L, 2L, 3L));
        return new PyrisWebhookService(mock(PyrisConnectorService.class), jobService, PyrisCallbackTokenTestUtil.tokenService(), settings, Optional.empty(),
                Optional.of(transcriptions), new VideoSourceResolverService(Optional.of(api), Optional.empty(), youTubeUrlService));
    }

    private static AttachmentVideoUnit tumLiveUnit() {
        Course course = new Course();
        course.setId(1L);
        Lecture lecture = new Lecture();
        lecture.setId(2L);
        lecture.setCourse(course);
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(3L);
        unit.setLecture(lecture);
        unit.setVideoSource(TUM_LIVE_VIDEO);
        return unit;
    }

    private void storeTranscript(AttachmentVideoUnit unit, TranscriptionStatus status) {
        LectureTranscription transcription = new LectureTranscription("de", List.of(new LectureTranscriptionSegment(0.0, 10.0, "Hallo", 1)), unit);
        transcription.setTranscriptionStatus(status);
        when(transcriptions.findByLectureUnit_Id(unit.getId())).thenReturn(Optional.of(transcription));
    }

    /**
     * A unit whose only content is a video TUM Live cannot resolve right now has nothing to ingest. Sent without the video, Iris would fail it for good,
     * so preparation fails with the dedicated, retryable error before any job token is registered.
     */
    @Test
    void unresolvableVideoWithoutOtherContentFailsThePreparationWithoutRegisteringAJob() {
        var svc = withTumLiveOutcome(new TumLivePlaylistLookup(null, TumLivePlaylistLookup.Outcome.NOT_ACCESSIBLE));
        AttachmentVideoUnit unit = tumLiveUnit();
        when(transcriptions.findByLectureUnit_Id(unit.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.prepareLectureUnitIngestion(unit, FULL_FINGERPRINT, FINGERPRINT_WITHOUT_VIDEO, false)).isInstanceOf(IrisVideoSourceUnavailableException.class);
        verify(jobService, never()).addLectureIngestionWebhookJob(anyLong(), anyLong(), anyLong());
    }

    /** A transcript that is not complete is not content Iris can rely on, so the unit still waits for its video. */
    @Test
    void unresolvableVideoWithAnIncompleteTranscriptFailsThePreparation() {
        var svc = withTumLiveOutcome(new TumLivePlaylistLookup(null, TumLivePlaylistLookup.Outcome.NOT_AVAILABLE));
        AttachmentVideoUnit unit = tumLiveUnit();
        storeTranscript(unit, TranscriptionStatus.PENDING);

        assertThatThrownBy(() -> svc.prepareLectureUnitIngestion(unit, FULL_FINGERPRINT, FINGERPRINT_WITHOUT_VIDEO, false)).isInstanceOf(IrisVideoSourceUnavailableException.class);
    }

    /**
     * A completed transcript already holds what the video adds: the unit is sent without the video but with its transcript, under the full fingerprint, so it
     * counts as complete and is not tried again.
     */
    @Test
    void unresolvableVideoWithACompletedTranscriptIsIngestedWithTheTranscriptAndTheFullFingerprint() {
        var svc = withTumLiveOutcome(new TumLivePlaylistLookup(null, TumLivePlaylistLookup.Outcome.TEMPORARILY_UNAVAILABLE));
        AttachmentVideoUnit unit = tumLiveUnit();
        storeTranscript(unit, TranscriptionStatus.COMPLETED);

        var prepared = svc.prepareLectureUnitIngestion(unit, FULL_FINGERPRINT, FINGERPRINT_WITHOUT_VIDEO, false);

        assertThat(prepared.includesVideo()).isFalse();
        assertThat(prepared.contentFingerprint()).isEqualTo(FULL_FINGERPRINT);
        assertThat(prepared.executionDTO().pyrisLectureUnit().transcription()).isNotNull();
    }

    /**
     * The production case: a TUM Live course that is not public, and a unit with slides. The slides are ingested now, without the video, under the
     * fingerprint of the content without the video, which tells the reconcile to try the video again later.
     */
    @Test
    void unresolvableVideoWithAStoredPdfIsIngestedWithoutTheVideo() throws IOException {
        var svc = withTumLiveOutcome(new TumLivePlaylistLookup(null, TumLivePlaylistLookup.Outcome.NOT_ACCESSIBLE));
        AttachmentVideoUnit unit = tumLiveUnit();
        when(transcriptions.findByLectureUnit_Id(unit.getId())).thenReturn(Optional.empty());
        Path originalFileUploadPath = FilePathConverter.getFileUploadPath();
        FilePathConverter.setFileUploadPath(tempDir);
        try {
            Attachment attachment = new Attachment();
            attachment.setAttachmentType(AttachmentType.FILE);
            attachment.setLink("slides.pdf");
            attachment.setVersion(1);
            attachment.setAttachmentVideoUnit(unit);
            unit.setAttachment(attachment);
            Path pdf = attachment.fileLocation().orElseThrow().path();
            FileUtils.writeByteArrayToFile(pdf.toFile(), "%PDF-1.4 slides".getBytes(StandardCharsets.UTF_8));

            var prepared = svc.prepareLectureUnitIngestion(unit, FULL_FINGERPRINT, FINGERPRINT_WITHOUT_VIDEO, false);

            assertThat(prepared.includesVideo()).isFalse();
            assertThat(prepared.contentFingerprint()).isEqualTo(FINGERPRINT_WITHOUT_VIDEO);
            assertThat(prepared.executionDTO().pyrisLectureUnit().pdfFile()).isNotEmpty();
            assertThat(prepared.executionDTO().pyrisLectureUnit().videoLink()).isNull();
        }
        finally {
            // The path is process-wide; put back what was there, also when nothing was set yet
            if (originalFileUploadPath != null) {
                FilePathConverter.setFileUploadPath(originalFileUploadPath);
            }
            else {
                ReflectionTestUtils.setField(FilePathConverter.class, "fileUploadPath", null);
            }
        }
    }

    /** A video that resolves is sent as before, under the full fingerprint. */
    @Test
    void resolvableVideoIsSentWithTheFullFingerprint() {
        var svc = withTumLiveOutcome(new TumLivePlaylistLookup("https://live.rbg.tum.de/pl.m3u8", TumLivePlaylistLookup.Outcome.RESOLVED));
        AttachmentVideoUnit unit = tumLiveUnit();
        when(transcriptions.findByLectureUnit_Id(unit.getId())).thenReturn(Optional.empty());

        var prepared = svc.prepareLectureUnitIngestion(unit, FULL_FINGERPRINT, FINGERPRINT_WITHOUT_VIDEO, false);

        assertThat(prepared.includesVideo()).isTrue();
        assertThat(prepared.contentFingerprint()).isEqualTo(FULL_FINGERPRINT);
        assertThat(prepared.executionDTO().pyrisLectureUnit().videoLink()).isEqualTo("https://live.rbg.tum.de/pl.m3u8");
    }
}
