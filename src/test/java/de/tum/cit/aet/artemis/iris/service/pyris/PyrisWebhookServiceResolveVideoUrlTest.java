package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.exception.IrisInternalPyrisErrorException;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.videosource.api.TumLiveApi;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;
import de.tum.cit.aet.artemis.videosource.service.YouTubeUrlService;

class PyrisWebhookServiceResolveVideoUrlTest {

    private final YouTubeUrlService youTubeUrlService = new YouTubeUrlService();

    private PyrisWebhookService withTumLive(TumLiveApi tumLiveApi) {
        VideoSourceResolverService resolver = new VideoSourceResolverService(Optional.ofNullable(tumLiveApi), youTubeUrlService);
        return new PyrisWebhookService(mock(PyrisConnectorService.class), mock(PyrisJobService.class), mock(IrisSettingsService.class), Optional.empty(), Optional.empty(),
                resolver);
    }

    @Test
    void tumLiveMatchReturnsTumLiveType() {
        var api = mock(TumLiveApi.class);
        when(api.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://live.rbg.tum.de/pl.m3u8"));
        var svc = withTumLive(api);
        var resolved = svc.resolveVideoUrl("https://live.rbg.tum.de/?course=foo&streamId=1");
        assertThat(resolved.url()).isEqualTo("https://live.rbg.tum.de/pl.m3u8");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
    }

    @Test
    void youTubeUrlReturnsYouTubeType() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("https://youtu.be/dQw4w9WgXcQ");
        assertThat(resolved.url()).isEqualTo("https://youtu.be/dQw4w9WgXcQ");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }

    @Test
    void unknownSourceReturnsNullType() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("https://vimeo.com/123");
        assertThat(resolved.url()).isEqualTo("https://vimeo.com/123");
        assertThat(resolved.type()).isNull();
    }

    @Test
    void nullUrlReturnsNullResolution() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl(null);
        assertThat(resolved.url()).isNull();
        assertThat(resolved.type()).isNull();
    }

    @Test
    void blankUrlReturnsBlankResolution() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("   ");
        assertThat(resolved.url()).isEqualTo("   ");
        assertThat(resolved.type()).isNull();
    }

    @Test
    void tumLiveApiExceptionFallsBackToOriginalUrlWithNullType() {
        var api = mock(TumLiveApi.class);
        when(api.getTumLivePlaylistLink(any())).thenThrow(new RuntimeException("boom"));
        var svc = withTumLive(api);
        var resolved = svc.resolveVideoUrl("https://live.rbg.tum.de/?x=1");
        assertThat(resolved.url()).isEqualTo("https://live.rbg.tum.de/?x=1");
        assertThat(resolved.type()).isNull();
    }

    @Test
    void tumLiveCheckedBeforeYouTubeSoTumLiveWins() {
        var api = mock(TumLiveApi.class);
        when(api.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://live.rbg.tum.de/resolved.m3u8"));
        var svc = withTumLive(api);
        var resolved = svc.resolveVideoUrl("https://live.rbg.tum.de/foo");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
    }

    @Test
    void absentTumLiveApiYouTubeStillResolves() {
        var svc = withTumLive(null);
        var resolved = svc.resolveVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }

    /**
     * A TUM Live link that TUM Live cannot resolve right now is still a supported video. Preparing the job without it would make Iris delete
     * the unit's transcript, so preparation fails (and the claim is retried later) before any job token is registered.
     */
    @Test
    void unresolvableSupportedVideoFailsThePreparationWithoutRegisteringAJob() {
        var api = mock(TumLiveApi.class);
        when(api.isTumLiveUrl("https://live.rbg.tum.de/w/course/1")).thenReturn(true);
        when(api.getTumLivePlaylistLink(any())).thenReturn(Optional.empty());
        IrisSettingsService settings = mock(IrisSettingsService.class);
        when(settings.isEnabledForCourse(any(Course.class))).thenReturn(true);
        LectureTranscriptionsRepositoryApi transcriptions = mock(LectureTranscriptionsRepositoryApi.class);
        when(transcriptions.findByLectureUnit_Id(anyLong())).thenReturn(Optional.empty());
        PyrisJobService jobService = mock(PyrisJobService.class);
        var svc = new PyrisWebhookService(mock(PyrisConnectorService.class), jobService, settings, Optional.empty(), Optional.of(transcriptions),
                new VideoSourceResolverService(Optional.of(api), youTubeUrlService));
        Course course = new Course();
        course.setId(1L);
        Lecture lecture = new Lecture();
        lecture.setId(2L);
        lecture.setCourse(course);
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(3L);
        unit.setLecture(lecture);
        unit.setVideoSource("https://live.rbg.tum.de/w/course/1");

        assertThatThrownBy(() -> svc.prepareLectureUnitIngestion(unit, "v1:fp", false)).isInstanceOf(IrisInternalPyrisErrorException.class);
        verify(jobService, never()).addLectureIngestionWebhookJob(anyLong(), anyLong(), anyLong());
    }
}
