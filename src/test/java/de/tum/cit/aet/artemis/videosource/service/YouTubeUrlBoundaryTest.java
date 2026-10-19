package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeUrlBoundaryTest {

    private final YouTubeUrlService service = new YouTubeUrlService();

    @ParameterizedTest
    @ValueSource(strings = { "https://www.youtube.com/watch/?v=dQw4w9WgXcQ", "https://youtu.be/dQw4w9WgXcQ/extra", "https://www.youtube.com/embed/dQw4w9WgXcQ/extra",
            "https://youtube.com/watch?ignored&=value&v=&v=dQw4w9WgXcQ", "https://youtube.com/watch?other=value&v=dQw4w9WgXcQ" })
    void extractsOnlyTheVideoIdFromSupportedPathsAndSkipsEmptyOrUnrelatedQueryPairs(String url) {
        assertThat(service.hasYouTubeHost(url)).isTrue();
        assertThat(service.extractYouTubeVideoId(url)).contains("dQw4w9WgXcQ");
        assertThat(service.isYouTubeUrl(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://youtu.be", "https://youtu.be/", "https://youtu.be//", "https://youtu.be/%20", "https://youtube.com/embed/", "https://youtube.com/shorts/",
            "https://youtube.com/live/", "https://youtube.com/embed//", "https://youtube.com/embed/%20", "https://youtube.com/watch?v=", "https://youtube.com/watch?other=value",
            "https://youtube.com/watch?=value&ignored&v=", "https://youtube.com/unknown?v=dQw4w9WgXcQ" })
    void recognizedHostDoesNotMakeAnIncompleteVideoUrlValid(String url) {
        assertThat(service.hasYouTubeHost(url)).isTrue();
        assertThat(service.extractYouTubeVideoId(url)).isEmpty();
        assertThat(service.isYouTubeUrl(url)).isFalse();
    }
}
