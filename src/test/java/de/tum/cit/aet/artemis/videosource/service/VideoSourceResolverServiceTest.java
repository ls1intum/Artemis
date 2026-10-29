package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.videosource.api.GocastBindingApi;
import de.tum.cit.aet.artemis.videosource.api.TumLiveApi;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

class VideoSourceResolverServiceTest {

    private final YouTubeUrlService youTubeUrlService = new YouTubeUrlService();

    private VideoSourceResolverService withTumLive(TumLiveApi tumLiveApi) {
        return new VideoSourceResolverService(Optional.ofNullable(tumLiveApi), Optional.empty(), youTubeUrlService);
    }

    // ── null / blank ─────────────────────────────────────────────────────────

    @Test
    void nullUrlReturnsNullTypeAndNullUrl() {
        var resolved = withTumLive(null).resolve(null, 1L);
        assertThat(resolved.url()).isNull();
        assertThat(resolved.type()).isNull();
        assertThat(resolved.youtubeVideoId()).isNull();
    }

    @Test
    void blankUrlReturnsNullTypeAndOriginalUrl() {
        var resolved = withTumLive(null).resolve("   ", 1L);
        assertThat(resolved.url()).isEqualTo("   ");
        assertThat(resolved.type()).isNull();
        assertThat(resolved.youtubeVideoId()).isNull();
    }

    // ── TUM Live absent ───────────────────────────────────────────────────────

    @Test
    void absentTumLiveApiYouTubeUrlReturnsYouTubeType() {
        var resolved = withTumLive(null).resolve("https://youtu.be/dQw4w9WgXcQ", 1L);
        assertThat(resolved.url()).isEqualTo("https://youtu.be/dQw4w9WgXcQ");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
        assertThat(resolved.youtubeVideoId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void absentTumLiveApiYouTubeWatchUrlReturnsYouTubeType() {
        var resolved = withTumLive(null).resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ", 1L);
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
        assertThat(resolved.youtubeVideoId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void absentTumLiveApiNonYouTubeUrlReturnsNullType() {
        var resolved = withTumLive(null).resolve("https://vimeo.com/123456", 1L);
        assertThat(resolved.url()).isEqualTo("https://vimeo.com/123456");
        assertThat(resolved.type()).isNull();
        assertThat(resolved.youtubeVideoId()).isNull();
    }

    // ── TUM Live present ──────────────────────────────────────────────────────

    @Test
    void connectedCourseResolvesThroughItsGrantBeforeThePublicLookup() {
        var tumLiveApi = mock(TumLiveApi.class);
        var gocastBindingApi = mock(GocastBindingApi.class);
        when(gocastBindingApi.lookUpConnectedPlaylist(7L, "https://live.rbg.tum.de/w/algo/42"))
                .thenReturn(Optional.of(new TumLivePlaylistLookup("https://edge/signed.m3u8", TumLivePlaylistLookup.Outcome.RESOLVED)));
        var resolver = new VideoSourceResolverService(Optional.of(tumLiveApi), Optional.of(gocastBindingApi), youTubeUrlService);

        var resolved = resolver.resolve("https://live.rbg.tum.de/w/algo/42", 7L);

        assertThat(resolved.url()).isEqualTo("https://edge/signed.m3u8");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
        verify(tumLiveApi, never()).lookUpPlaylist(any());
    }

    @Test
    void lectureTheConnectionDoesNotCoverUsesThePublicLookup() {
        var tumLiveApi = mock(TumLiveApi.class);
        var gocastBindingApi = mock(GocastBindingApi.class);
        when(gocastBindingApi.lookUpConnectedPlaylist(7L, "https://live.rbg.tum.de/w/other/9")).thenReturn(Optional.empty());
        when(tumLiveApi.lookUpPlaylist("https://live.rbg.tum.de/w/other/9"))
                .thenReturn(new TumLivePlaylistLookup("https://edge/public.m3u8", TumLivePlaylistLookup.Outcome.RESOLVED));
        var resolver = new VideoSourceResolverService(Optional.of(tumLiveApi), Optional.of(gocastBindingApi), youTubeUrlService);

        assertThat(resolver.resolve("https://live.rbg.tum.de/w/other/9", 7L).url()).isEqualTo("https://edge/public.m3u8");
    }

    @Test
    void unavailableConnectedLectureIsNotResolvedPublicly() {
        var tumLiveApi = mock(TumLiveApi.class);
        var gocastBindingApi = mock(GocastBindingApi.class);
        when(gocastBindingApi.lookUpConnectedPlaylist(7L, "https://live.rbg.tum.de/w/algo/42"))
                .thenReturn(Optional.of(new TumLivePlaylistLookup(null, TumLivePlaylistLookup.Outcome.TEMPORARILY_UNAVAILABLE)));
        var resolver = new VideoSourceResolverService(Optional.of(tumLiveApi), Optional.of(gocastBindingApi), youTubeUrlService);

        var resolved = resolver.resolve("https://live.rbg.tum.de/w/algo/42", 7L);

        assertThat(resolved.type()).isNull();
        verify(tumLiveApi, never()).lookUpPlaylist(any());
    }

    @Test
    void tumLiveMatchReturnsTumLiveTypeAndPlaylistUrl() {
        var api = mock(TumLiveApi.class);
        when(api.lookUpPlaylist(any())).thenReturn(new TumLivePlaylistLookup("https://live.rbg.tum.de/pl.m3u8", TumLivePlaylistLookup.Outcome.RESOLVED));
        var resolved = withTumLive(api).resolve("https://live.rbg.tum.de/?course=foo&streamId=1", 1L);
        assertThat(resolved.url()).isEqualTo("https://live.rbg.tum.de/pl.m3u8");
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
        assertThat(resolved.youtubeVideoId()).isNull();
    }

    @Test
    void tumLiveNoMatchFallsThroughToYouTubeCheck() {
        var api = mock(TumLiveApi.class);
        when(api.lookUpPlaylist(any())).thenReturn(new TumLivePlaylistLookup(null, TumLivePlaylistLookup.Outcome.NOT_ACCESSIBLE));
        var resolved = withTumLive(api).resolve("https://youtu.be/dQw4w9WgXcQ", 1L);
        assertThat(resolved.type()).isEqualTo(VideoSourceType.YOUTUBE);
        assertThat(resolved.youtubeVideoId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void tumLiveCheckedBeforeYouTube() {
        var api = mock(TumLiveApi.class);
        when(api.lookUpPlaylist(any())).thenReturn(new TumLivePlaylistLookup("https://live.rbg.tum.de/resolved.m3u8", TumLivePlaylistLookup.Outcome.RESOLVED));
        // URL is a YouTube URL, but TUM Live matches first
        var resolved = withTumLive(api).resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ", 1L);
        assertThat(resolved.type()).isEqualTo(VideoSourceType.TUM_LIVE);
        assertThat(resolved.youtubeVideoId()).isNull();
    }

    // ── TUM Live throws ───────────────────────────────────────────────────────

    @Test
    void tumLiveApiExceptionFallsBackToOriginalUrlWithNullType() {
        var api = mock(TumLiveApi.class);
        when(api.lookUpPlaylist(any())).thenThrow(new RuntimeException("boom"));
        var resolved = withTumLive(api).resolve("https://live.rbg.tum.de/?x=1", 1L);
        assertThat(resolved.url()).isEqualTo("https://live.rbg.tum.de/?x=1");
        assertThat(resolved.type()).isNull();
        assertThat(resolved.youtubeVideoId()).isNull();
    }

    @Test
    void tumLiveApiExceptionDoesNotFallThroughToYouTube() {
        var api = mock(TumLiveApi.class);
        when(api.lookUpPlaylist(any())).thenThrow(new RuntimeException("boom"));
        // The URL looks like a YouTube URL — but after a TUM Live exception we must return null type, not YOUTUBE
        var resolved = withTumLive(api).resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ", 1L);
        assertThat(resolved.type()).isNull();
        assertThat(resolved.youtubeVideoId()).isNull();
    }

    // ── supported source (URL shape only) ────────────────────────────────────

    @Test
    void youTubeUrlIsSupportedWithoutTumLive() {
        assertThat(withTumLive(null).isSupportedSource("https://youtu.be/dQw4w9WgXcQ")).isTrue();
    }

    @Test
    void tumLiveUrlIsSupportedOnlyWhenTumLiveIsConfiguredAndWithoutCallingIt() {
        var api = mock(TumLiveApi.class);
        when(api.isTumLiveUrl("https://live.rbg.tum.de/w/course/1")).thenReturn(true);

        assertThat(withTumLive(api).isSupportedSource("https://live.rbg.tum.de/w/course/1")).isTrue();
        assertThat(withTumLive(null).isSupportedSource("https://live.rbg.tum.de/w/course/1")).isFalse();
        verify(api, never()).lookUpPlaylist(any());
    }

    @Test
    void otherAndBlankSourcesAreNotSupported() {
        var api = mock(TumLiveApi.class);
        assertThat(withTumLive(api).isSupportedSource("https://vimeo.com/123456")).isFalse();
        assertThat(withTumLive(api).isSupportedSource(null)).isFalse();
        assertThat(withTumLive(api).isSupportedSource("   ")).isFalse();
    }
}
