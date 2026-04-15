package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.videosource.api.TumLiveApi;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolver;
import de.tum.cit.aet.artemis.videosource.service.YouTubeUrlService;

class PyrisWebhookServiceResolveVideoUrlTest {

    private final YouTubeUrlService youTubeUrlService = new YouTubeUrlService();

    private PyrisWebhookService withTumLive(TumLiveApi tumLiveApi) {
        VideoSourceResolver resolver = new VideoSourceResolver(Optional.ofNullable(tumLiveApi), youTubeUrlService);
        return new PyrisWebhookService(mock(PyrisConnectorService.class), mock(PyrisJobService.class), mock(IrisSettingsService.class), Optional.empty(), Optional.empty(),
                Optional.empty(), resolver);
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
}
