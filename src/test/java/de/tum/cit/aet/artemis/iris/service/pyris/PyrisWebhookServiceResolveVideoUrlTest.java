package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService.ResolvedVideo;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.VideoSourceType;
import de.tum.cit.aet.artemis.tumlive.api.TumLiveApi;
import de.tum.cit.aet.artemis.tumlive.api.YouTubeApi;

/**
 * Unit tests for {@link PyrisWebhookService#resolveVideoUrl(String)}, covering the
 * TUM Live → HLS resolution path, YouTube passthrough, and all fallback cases.
 * Uses Mockito instead of a Spring context because the method only depends on
 * {@link TumLiveApi} and {@link YouTubeApi}.
 */
class PyrisWebhookServiceResolveVideoUrlTest {

    private TumLiveApi tumLiveApi;

    private YouTubeApi youTubeApi;

    private PyrisWebhookService service;

    @BeforeEach
    void setUp() {
        tumLiveApi = mock(TumLiveApi.class);
        youTubeApi = mock(YouTubeApi.class);
        service = new PyrisWebhookService(mock(PyrisConnectorService.class), mock(PyrisJobService.class), mock(IrisSettingsService.class), Optional.<LectureRepositoryApi>empty(),
                Optional.<LectureUnitRepositoryApi>empty(), Optional.<LectureTranscriptionsRepositoryApi>empty(), Optional.of(tumLiveApi), Optional.of(youTubeApi));
    }

    @Test
    void resolvesTumLiveUrlToHlsPlaylistAndTagsTumLive() {
        String tumLiveUrl = "https://live.rbg.tum.de/w/course/12345";
        String hlsPlaylist = "https://live.rbg.tum.de/vod/course/12345.m3u8";
        when(tumLiveApi.getTumLivePlaylistLink(tumLiveUrl)).thenReturn(Optional.of(hlsPlaylist));

        ResolvedVideo resolved = service.resolveVideoUrl(tumLiveUrl);

        assertThat(resolved.url()).isEqualTo(hlsPlaylist);
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
        verifyNoInteractions(youTubeApi);
    }

    @Test
    void passesYouTubeUrlThroughWhenTumLiveDoesNotMatch() {
        String youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        when(tumLiveApi.getTumLivePlaylistLink(youtubeUrl)).thenReturn(Optional.empty());
        when(youTubeApi.isYouTubeUrl(youtubeUrl)).thenReturn(true);

        ResolvedVideo resolved = service.resolveVideoUrl(youtubeUrl);

        assertThat(resolved.url()).isEqualTo(youtubeUrl);
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }

    @Test
    void prefersTumLiveOverYouTubeWhenBothWouldMatch() {
        String tumLiveUrl = "https://live.rbg.tum.de/w/course/12345";
        String hlsPlaylist = "https://live.rbg.tum.de/vod/course/12345.m3u8";
        when(tumLiveApi.getTumLivePlaylistLink(tumLiveUrl)).thenReturn(Optional.of(hlsPlaylist));

        ResolvedVideo resolved = service.resolveVideoUrl(tumLiveUrl);

        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
        verifyNoInteractions(youTubeApi);
    }

    @Test
    void returnsPassthroughWithNullTypeForUnknownSource() {
        String vimeoUrl = "https://vimeo.com/123456789";
        when(tumLiveApi.getTumLivePlaylistLink(vimeoUrl)).thenReturn(Optional.empty());
        when(youTubeApi.isYouTubeUrl(vimeoUrl)).thenReturn(false);

        ResolvedVideo resolved = service.resolveVideoUrl(vimeoUrl);

        assertThat(resolved.url()).isEqualTo(vimeoUrl);
        assertThat(resolved.type()).isNull();
    }

    @Test
    void fallsBackToPassthroughWhenTumLiveApiThrowsAndUrlIsNotYouTube() {
        String videoUrl = "https://example.com/weird";
        when(tumLiveApi.getTumLivePlaylistLink(videoUrl)).thenThrow(new RuntimeException("boom"));
        when(youTubeApi.isYouTubeUrl(videoUrl)).thenReturn(false);

        ResolvedVideo resolved = service.resolveVideoUrl(videoUrl);

        assertThat(resolved.url()).isEqualTo(videoUrl);
        assertThat(resolved.type()).isNull();
    }

    @Test
    void stillDetectsYouTubeWhenTumLiveApiThrows() {
        String youtubeUrl = "https://youtu.be/dQw4w9WgXcQ";
        when(tumLiveApi.getTumLivePlaylistLink(youtubeUrl)).thenThrow(new RuntimeException("boom"));
        when(youTubeApi.isYouTubeUrl(youtubeUrl)).thenReturn(true);

        ResolvedVideo resolved = service.resolveVideoUrl(youtubeUrl);

        assertThat(resolved.url()).isEqualTo(youtubeUrl);
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }

    @Test
    void returnsNullTypeForNullInputWithoutCallingAnyApi() {
        ResolvedVideo resolved = service.resolveVideoUrl(null);

        assertThat(resolved.url()).isNull();
        assertThat(resolved.type()).isNull();
        verifyNoInteractions(tumLiveApi);
        verifyNoInteractions(youTubeApi);
    }

    @Test
    void returnsNullTypeForBlankInputWithoutCallingAnyApi() {
        ResolvedVideo resolved = service.resolveVideoUrl("   ");

        assertThat(resolved.url()).isEqualTo("   ");
        assertThat(resolved.type()).isNull();
        verifyNoInteractions(tumLiveApi);
        verifyNoInteractions(youTubeApi);
    }

    @Test
    void worksWhenTumLiveApiIsAbsent() {
        service = new PyrisWebhookService(mock(PyrisConnectorService.class), mock(PyrisJobService.class), mock(IrisSettingsService.class), Optional.<LectureRepositoryApi>empty(),
                Optional.<LectureUnitRepositoryApi>empty(), Optional.<LectureTranscriptionsRepositoryApi>empty(), Optional.<TumLiveApi>empty(), Optional.of(youTubeApi));
        String youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        when(youTubeApi.isYouTubeUrl(youtubeUrl)).thenReturn(true);

        ResolvedVideo resolved = service.resolveVideoUrl(youtubeUrl);

        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }

    @Test
    void worksWhenYouTubeApiIsAbsent() {
        service = new PyrisWebhookService(mock(PyrisConnectorService.class), mock(PyrisJobService.class), mock(IrisSettingsService.class), Optional.<LectureRepositoryApi>empty(),
                Optional.<LectureUnitRepositoryApi>empty(), Optional.<LectureTranscriptionsRepositoryApi>empty(), Optional.of(tumLiveApi), Optional.<YouTubeApi>empty());
        String youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        when(tumLiveApi.getTumLivePlaylistLink(youtubeUrl)).thenReturn(Optional.empty());

        ResolvedVideo resolved = service.resolveVideoUrl(youtubeUrl);

        assertThat(resolved.url()).isEqualTo(youtubeUrl);
        assertThat(resolved.type()).isNull();
        verify(tumLiveApi).getTumLivePlaylistLink(any());
    }

    @Test
    void worksWhenBothNebulaApisAreAbsent() {
        service = new PyrisWebhookService(mock(PyrisConnectorService.class), mock(PyrisJobService.class), mock(IrisSettingsService.class), Optional.<LectureRepositoryApi>empty(),
                Optional.<LectureUnitRepositoryApi>empty(), Optional.<LectureTranscriptionsRepositoryApi>empty(), Optional.<TumLiveApi>empty(), Optional.<YouTubeApi>empty());

        ResolvedVideo resolved = service.resolveVideoUrl("https://any.example.com/video");

        assertThat(resolved.type()).isNull();
    }
}
