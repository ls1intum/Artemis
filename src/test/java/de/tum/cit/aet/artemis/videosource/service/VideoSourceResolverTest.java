package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.videosource.api.TumLiveApi;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

class VideoSourceResolverTest {

    private final YouTubeUrlService youTubeUrlService = new YouTubeUrlService();

    private VideoSourceResolver withTumLive(TumLiveApi tumLiveApi) {
        return new VideoSourceResolver(Optional.ofNullable(tumLiveApi), youTubeUrlService);
    }

    // ── null / blank ─────────────────────────────────────────────────────────

    @Test
    void nullUrlReturnsNullTypeAndNullUrl() {
        var resolved = withTumLive(null).resolve(null);
        assertThat(resolved.url()).isNull();
        assertThat(resolved.type()).isNull();
    }

    @Test
    void blankUrlReturnsNullTypeAndOriginalUrl() {
        var resolved = withTumLive(null).resolve("   ");
        assertThat(resolved.url()).isEqualTo("   ");
        assertThat(resolved.type()).isNull();
    }

    // ── TUM Live absent ───────────────────────────────────────────────────────

    @Test
    void absentTumLiveApiYouTubeUrlReturnsYouTubeType() {
        var resolved = withTumLive(null).resolve("https://youtu.be/dQw4w9WgXcQ");
        assertThat(resolved.url()).isEqualTo("https://youtu.be/dQw4w9WgXcQ");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }

    @Test
    void absentTumLiveApiYouTubeWatchUrlReturnsYouTubeType() {
        var resolved = withTumLive(null).resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }

    @Test
    void absentTumLiveApiNonYouTubeUrlReturnsNullType() {
        var resolved = withTumLive(null).resolve("https://vimeo.com/123456");
        assertThat(resolved.url()).isEqualTo("https://vimeo.com/123456");
        assertThat(resolved.type()).isNull();
    }

    // ── TUM Live present ──────────────────────────────────────────────────────

    @Test
    void tumLiveMatchReturnsTumLiveTypeAndPlaylistUrl() {
        var api = mock(TumLiveApi.class);
        when(api.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://live.rbg.tum.de/pl.m3u8"));
        var resolved = withTumLive(api).resolve("https://live.rbg.tum.de/?course=foo&streamId=1");
        assertThat(resolved.url()).isEqualTo("https://live.rbg.tum.de/pl.m3u8");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
    }

    @Test
    void tumLiveNoMatchFallsThroughToYouTubeCheck() {
        var api = mock(TumLiveApi.class);
        when(api.getTumLivePlaylistLink(any())).thenReturn(Optional.empty());
        var resolved = withTumLive(api).resolve("https://youtu.be/dQw4w9WgXcQ");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
    }

    @Test
    void tumLiveCheckedBeforeYouTube() {
        var api = mock(TumLiveApi.class);
        when(api.getTumLivePlaylistLink(any())).thenReturn(Optional.of("https://live.rbg.tum.de/resolved.m3u8"));
        // URL is a YouTube URL, but TUM Live matches first
        var resolved = withTumLive(api).resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
    }

    // ── TUM Live throws ───────────────────────────────────────────────────────

    @Test
    void tumLiveApiExceptionFallsBackToOriginalUrlWithNullType() {
        var api = mock(TumLiveApi.class);
        when(api.getTumLivePlaylistLink(any())).thenThrow(new RuntimeException("boom"));
        var resolved = withTumLive(api).resolve("https://live.rbg.tum.de/?x=1");
        assertThat(resolved.url()).isEqualTo("https://live.rbg.tum.de/?x=1");
        assertThat(resolved.type()).isNull();
    }

    @Test
    void tumLiveApiExceptionDoesNotFallThroughToYouTube() {
        var api = mock(TumLiveApi.class);
        when(api.getTumLivePlaylistLink(any())).thenThrow(new RuntimeException("boom"));
        // The URL looks like a YouTube URL — but after a TUM Live exception we must return null type, not YOUTUBE
        var resolved = withTumLive(api).resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(resolved.type()).isNull();
    }
}
