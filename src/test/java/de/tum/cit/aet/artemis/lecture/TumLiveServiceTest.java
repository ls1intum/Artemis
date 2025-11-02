package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.tum.cit.aet.artemis.lecture.dto.TumLivePlaylistDTO;
import de.tum.cit.aet.artemis.lecture.dto.TumLivePlaylistDTO.StreamDTO;
import de.tum.cit.aet.artemis.nebula.service.TumLiveService;

@ExtendWith(MockitoExtension.class)
class TumLiveServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    @Mock
    private RestClient.Builder restClientBuilder;

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
}
