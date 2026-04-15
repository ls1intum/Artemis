package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeUrlServiceTest {

    private final YouTubeUrlService service = new YouTubeUrlService();

    static Stream<Arguments> validUrlsAndIds() {
        return Stream.of(Arguments.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ"), Arguments.of("http://youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ"),
                Arguments.of("https://m.youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ"), Arguments.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share", "dQw4w9WgXcQ"),
                Arguments.of("https://youtu.be/dQw4w9WgXcQ", "dQw4w9WgXcQ"), Arguments.of("https://youtu.be/dQw4w9WgXcQ?t=42", "dQw4w9WgXcQ"),
                Arguments.of("https://www.youtube.com/embed/dQw4w9WgXcQ", "dQw4w9WgXcQ"), Arguments.of("https://www.youtube.com/live/dQw4w9WgXcQ", "dQw4w9WgXcQ"),
                Arguments.of("https://www.youtube.com/shorts/dQw4w9WgXcQ", "dQw4w9WgXcQ"), Arguments.of("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ", "dQw4w9WgXcQ"),
                Arguments.of("HTTPS://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ"));
    }

    @ParameterizedTest
    @MethodSource("validUrlsAndIds")
    void extractsVideoIdFromValidUrl(String url, String expected) {
        assertThat(service.extractYouTubeVideoId(url)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "not a url", "https://vimeo.com/123", "https://notyoutube.com/watch?v=dQw4w9WgXcQ", "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
            "https://fakeyoutu.be/dQw4w9WgXcQ", "ftp://youtube.com/watch?v=dQw4w9WgXcQ", "javascript:alert(1)", "data:text/html,hi", "https://www.youtube.com/watch",
            "https://www.youtube.com/watch?v=short", "https://youtu.be/" })
    void rejectsInvalidUrl(String url) {
        assertThat(service.extractYouTubeVideoId(url)).isEmpty();
    }

    @Test
    void rejectsNullUrl() {
        assertThat(service.extractYouTubeVideoId(null)).isEmpty();
    }

    @Test
    void isYouTubeUrlDelegatesToExtraction() {
        assertThat(service.isYouTubeUrl("https://youtu.be/dQw4w9WgXcQ")).isTrue();
        assertThat(service.isYouTubeUrl("https://vimeo.com/123")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://youtube.com/watch?v=bogus-id-goes-here", "https://m.youtube.com/watch", "https://YOUTU.BE/anything", "https://www.YouTube.Com/ANY",
            "https://youtube-nocookie.com/garbage" })
    void hasYouTubeHostTrueForYouTubeHostsIrrespectiveOfPath(String url) {
        assertThat(service.hasYouTubeHost(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://vimeo.com/123", "https://notyoutube.com/watch?v=dQw4w9WgXcQ", "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
            "https://fakeyoutu.be/dQw4w9WgXcQ", "ftp://www.youtube.com/watch?v=dQw4w9WgXcQ" })
    void hasYouTubeHostFalseForSpoofsAndNonHttp(String url) {
        assertThat(service.hasYouTubeHost(url)).isFalse();
    }

    @Test
    void hostSetInvariant_extractableImpliesHasYouTubeHost() {
        validUrlsAndIds().forEach(args -> {
            String url = (String) args.get()[0];
            assertThat(service.hasYouTubeHost(url)).as("hasYouTubeHost should be true for extractable URL: %s", url).isTrue();
        });
    }
}
