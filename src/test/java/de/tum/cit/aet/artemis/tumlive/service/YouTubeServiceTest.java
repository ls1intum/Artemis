package de.tum.cit.aet.artemis.tumlive.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeServiceTest {

    private YouTubeService youTubeService;

    @BeforeEach
    void setUp() {
        youTubeService = new YouTubeService();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://youtube.com/watch?v=dQw4w9WgXcQ", "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=120", "https://www.youtube.com/watch?list=PLtest&v=dQw4w9WgXcQ" })
    void shouldExtractVideoIdFromWatchUrls(String url) {
        Optional<String> videoId = youTubeService.extractYouTubeVideoId(url);
        assertThat(videoId).contains("dQw4w9WgXcQ");
    }

    @Test
    void shouldExtractVideoIdFromShortenedUrl() {
        Optional<String> videoId = youTubeService.extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ");
        assertThat(videoId).contains("dQw4w9WgXcQ");
    }

    @Test
    void shouldExtractVideoIdFromEmbedUrl() {
        Optional<String> videoId = youTubeService.extractYouTubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ");
        assertThat(videoId).contains("dQw4w9WgXcQ");
    }

    @Test
    void shouldExtractVideoIdFromLiveUrl() {
        Optional<String> videoId = youTubeService.extractYouTubeVideoId("https://www.youtube.com/live/dQw4w9WgXcQ");
        assertThat(videoId).contains("dQw4w9WgXcQ");
    }

    @Test
    void shouldExtractVideoIdFromShortsUrl() {
        Optional<String> videoId = youTubeService.extractYouTubeVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ");
        assertThat(videoId).contains("dQw4w9WgXcQ");
    }

    @Test
    void shouldExtractVideoIdFromNoCookieEmbedUrl() {
        Optional<String> videoId = youTubeService.extractYouTubeVideoId("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");
        assertThat(videoId).contains("dQw4w9WgXcQ");
    }

    @Test
    void shouldExtractVideoIdWithHyphensAndUnderscores() {
        Optional<String> videoId = youTubeService.extractYouTubeVideoId("https://www.youtube.com/watch?v=a-B_c1D2e3F");
        assertThat(videoId).contains("a-B_c1D2e3F");
    }

    @Test
    void shouldExtractVideoIdFromShortenedUrlWithQueryParams() {
        assertThat(youTubeService.extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ?t=42")).contains("dQw4w9WgXcQ");
    }

    @Test
    void shouldExtractVideoIdFromEmbedUrlWithQueryParams() {
        assertThat(youTubeService.extractYouTubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ?autoplay=1")).contains("dQw4w9WgXcQ");
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://live.rbg.tum.de/w/course/12345", "https://vimeo.com/123456789", "https://example.com/video.mp4", "not-a-url", "" })
    void shouldReturnEmptyForNonYouTubeUrls(String url) {
        Optional<String> videoId = youTubeService.extractYouTubeVideoId(url);
        assertThat(videoId).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://notyoutube.com/watch?v=dQw4w9WgXcQ", "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ", "https://fakeyoutu.be/dQw4w9WgXcQ" })
    void shouldRejectSpoofedYouTubeDomains(String url) {
        Optional<String> videoId = youTubeService.extractYouTubeVideoId(url);
        assertThat(videoId).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNullUrl() {
        Optional<String> videoId = youTubeService.extractYouTubeVideoId(null);
        assertThat(videoId).isEmpty();
    }

    @Test
    void shouldDetectYouTubeUrl() {
        assertThat(youTubeService.isYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).isTrue();
        assertThat(youTubeService.isYouTubeUrl("https://youtu.be/dQw4w9WgXcQ")).isTrue();
    }

    @Test
    void shouldNotDetectNonYouTubeUrl() {
        assertThat(youTubeService.isYouTubeUrl("https://live.rbg.tum.de/w/course/12345")).isFalse();
        assertThat(youTubeService.isYouTubeUrl(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://YouTube.com/watch?v=dQw4w9WgXcQ", "https://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ", "https://Youtu.Be/dQw4w9WgXcQ",
            "https://www.YouTube-NoCookie.com/embed/dQw4w9WgXcQ" })
    void shouldAcceptMixedCaseHosts(String url) {
        assertThat(youTubeService.extractYouTubeVideoId(url)).contains("dQw4w9WgXcQ");
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp://youtube.com/watch?v=dQw4w9WgXcQ", "file:///youtube.com/watch?v=dQw4w9WgXcQ", "javascript:alert('xss')",
            "data:text/html,<script>youtube.com/watch?v=dQw4w9WgXcQ</script>" })
    void shouldRejectNonHttpSchemes(String url) {
        assertThat(youTubeService.extractYouTubeVideoId(url)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // over-long id in watch?v=...
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ123",
            // too-short id in watch?v=...
            "https://www.youtube.com/watch?v=short",
            // over-long id in path-style URLs
            "https://www.youtube.com/embed/dQw4w9WgXcQ123", "https://www.youtube.com/shorts/dQw4w9WgXcQ123", "https://youtu.be/dQw4w9WgXcQ123",
            // non-video endpoints that embed a YouTube URL as a parameter
            "https://www.youtube.com/oembed?url=https://youtu.be/dQw4w9WgXcQ", "https://www.youtube.com/redirect?q=https://youtu.be/dQw4w9WgXcQ",
            // unknown paths on a YouTube host
            "https://www.youtube.com/", "https://www.youtube.com/feed/trending", "https://www.youtube.com/channel/UCtest", "https://www.youtube.com/user/someuser",
            // v param missing a value
            "https://www.youtube.com/watch?v=" })
    void shouldRejectMalformedOrNonVideoYouTubeUrls(String url) {
        assertThat(youTubeService.extractYouTubeVideoId(url)).isEmpty();
    }
}
