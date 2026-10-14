package de.tum.cit.aet.artemis.videosource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.tum.cit.aet.artemis.lecture.dto.TumLivePlaylistDTO;
import de.tum.cit.aet.artemis.lecture.dto.TumLivePlaylistDTO.StreamDTO;
import de.tum.cit.aet.artemis.videosource.service.TumLivePlaylistLookup;
import de.tum.cit.aet.artemis.videosource.service.TumLiveService;

@ExtendWith(MockitoExtension.class)
class TumLiveServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    @Mock
    private RestClient.Builder restClientBuilder;

    private static final String WATCH_URL = "https://live.rbg.tum.de/w/abc-course/12345";

    private TumLiveService tumLiveService;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(any(String.class))).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        tumLiveService = new TumLiveService(restClientBuilder, "https://api.tum.live");
    }

    @Test
    void getTumLivePlaylistLink_validUrl_returnsPlaylist() {
        var playlist = "https://cdn.tum.live/playlist_12345.m3u8";
        var dto = new TumLivePlaylistDTO(new StreamDTO(playlist));

        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("abc-course"), eq("12345")).retrieve().body(eq(TumLivePlaylistDTO.class))).thenReturn(dto);

        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/w/abc-course/12345");

        assertThat(result).isPresent().contains(playlist);
    }

    @Test
    void getTumLivePlaylistLink_notTumLiveHost_returnsEmpty() {
        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://example.com/some/path");
        assertThat(result).isEmpty();
    }

    @Test
    void getTumLivePlaylistLink_malformedUrl_returnsEmpty() {
        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/w/abc course/12345\nFORGED");
        assertThat(result).isEmpty();
    }

    @Test
    void getTumLivePlaylistLink_invalidPath_returnsEmpty() {
        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/invalid/path");
        assertThat(result).isEmpty();
    }

    @Test
    void getTumLivePlaylistLink_apiReturnsNoPlaylist_returnsEmpty() {
        var dto = new TumLivePlaylistDTO(null);
        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("courseXYZ"), eq("999")).retrieve().body(eq(TumLivePlaylistDTO.class))).thenReturn(dto);

        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/w/courseXYZ/999");
        assertThat(result).isEmpty();
    }

    @Test
    void getTumLivePlaylistLink_apiThrows_returnsEmpty() {
        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("abc-course"), eq("12345")).retrieve().body(eq(TumLivePlaylistDTO.class)))
                .thenThrow(new RestClientException("boom"));

        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/w/abc-course/12345");
        assertThat(result).isEmpty();
    }

    /** The production case: TUM Live refuses a course that is not public. The reason is kept, so it is not mistaken for TUM Live being down. */
    @Test
    void lookUpPlaylist_forbidden_isNotAccessible() {
        stubLookupThrows(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, null, null));
        assertThat(tumLiveService.lookUpPlaylist(WATCH_URL).outcome()).isEqualTo(TumLivePlaylistLookup.Outcome.NOT_ACCESSIBLE);
    }

    @Test
    void lookUpPlaylist_notFound_isNotAvailable() {
        stubLookupThrows(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));
        assertThat(tumLiveService.lookUpPlaylist(WATCH_URL).outcome()).isEqualTo(TumLivePlaylistLookup.Outcome.NOT_AVAILABLE);
    }

    /** A stream without a recording yet has no playlist URL. */
    @Test
    void lookUpPlaylist_noPlaylistUrl_isNotAvailable() {
        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("abc-course"), eq("12345")).retrieve().body(eq(TumLivePlaylistDTO.class)))
                .thenReturn(new TumLivePlaylistDTO(new StreamDTO(null)));
        assertThat(tumLiveService.lookUpPlaylist(WATCH_URL).outcome()).isEqualTo(TumLivePlaylistLookup.Outcome.NOT_AVAILABLE);
    }

    @Test
    void lookUpPlaylist_serverErrorOrConnectionFailure_isTemporarilyUnavailable() {
        stubLookupThrows(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway", HttpHeaders.EMPTY, null, null));
        assertThat(tumLiveService.lookUpPlaylist(WATCH_URL).outcome()).isEqualTo(TumLivePlaylistLookup.Outcome.TEMPORARILY_UNAVAILABLE);
    }

    @Test
    void lookUpPlaylist_success_isResolvedWithThePlaylist() {
        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("abc-course"), eq("12345")).retrieve().body(eq(TumLivePlaylistDTO.class)))
                .thenReturn(new TumLivePlaylistDTO(new StreamDTO("https://cdn.tum.live/pl.m3u8")));
        TumLivePlaylistLookup lookup = tumLiveService.lookUpPlaylist(WATCH_URL);
        assertThat(lookup.outcome()).isEqualTo(TumLivePlaylistLookup.Outcome.RESOLVED);
        assertThat(lookup.playlistUrl()).isEqualTo("https://cdn.tum.live/pl.m3u8");
    }

    @Test
    void lookUpPlaylist_notATumLiveUrl_isNotApplicable() {
        assertThat(tumLiveService.lookUpPlaylist("https://example.com/some/path").outcome()).isEqualTo(TumLivePlaylistLookup.Outcome.NOT_APPLICABLE);
    }

    private void stubLookupThrows(RestClientException exception) {
        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("abc-course"), eq("12345")).retrieve().body(eq(TumLivePlaylistDTO.class))).thenThrow(exception);
    }

    @Test
    void getTumLivePlaylistLink_clientNotConfigured_returnsEmpty() {
        TumLiveService unconfiguredService = new TumLiveService(restClientBuilder, " ");

        Optional<String> result = unconfiguredService.getTumLivePlaylistLink("https://live.rbg.tum.de/w/abc-course/12345");

        assertThat(result).isEmpty();
    }

    @Test
    void isTumLiveUrl_checksTheWatchPageShapeWithoutCallingTheApi() {
        assertThat(tumLiveService.isTumLiveUrl("https://live.rbg.tum.de/w/abc-course/12345")).isTrue();
        assertThat(tumLiveService.isTumLiveUrl("https://example.com/some/path")).isFalse();
        assertThat(tumLiveService.isTumLiveUrl("https://tum.live/w/abc-course/12345")).isTrue();
        assertThat(tumLiveService.isTumLiveUrl("https://api.tum.live/w/abc-course/12345")).as("the host of the configured API").isTrue();
        assertThat(tumLiveService.isTumLiveUrl("https://example.com/w/abc-course/12345")).as("a watch page path on another host").isFalse();
        assertThat(tumLiveService.isTumLiveUrl("mailto:lecturer@example.com")).as("an opaque URI has no path").isFalse();
        assertThat(tumLiveService.isTumLiveUrl("http://exa mple.com/w/a/1")).as("a malformed URL").isFalse();
        verify(restClient, never()).get();
    }
}
