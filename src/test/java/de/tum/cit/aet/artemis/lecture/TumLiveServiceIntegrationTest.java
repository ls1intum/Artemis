package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.tum.cit.aet.artemis.lecture.dto.TumLivePlaylistDTO;
import de.tum.cit.aet.artemis.lecture.dto.TumLivePlaylistDTO.StreamDTO;
import de.tum.cit.aet.artemis.lecture.service.TumLiveService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class TumLiveServiceIntTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private TumLiveService tumLiveService;

    // Deep-stubbed so get().uri(...).retrieve().body(...) works
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        // Inject our mock into the real bean
        ReflectionTestUtils.setField(tumLiveService, "restClient", restClient);
    }

    @Test
    void getTumLivePlaylistLink_validUrl_returnsPlaylist() {
        var playlist = "https://cdn.tum.live/playlist_12345.m3u8";
        var dto = new TumLivePlaylistDTO(new StreamDTO(playlist));

        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("abc-course"), eq("12345")).retrieve().body(any(ParameterizedTypeReference.class))).thenReturn(dto);

        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/w/abc-course/12345");

        assertThat(result).isPresent().contains(playlist);
        verify(restClient.get()).uri(eq("/streams/{courseSlug}/{streamId}"), eq("abc-course"), eq("12345"));
    }

    @Test
    void getTumLivePlaylistLink_notTumLiveHost_returnsEmpty() {
        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://example.com/some/path");
        assertThat(result).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    void getTumLivePlaylistLink_invalidPath_returnsEmpty() {
        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/invalid/path");
        assertThat(result).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    void getTumLivePlaylistLink_apiReturnsNoPlaylist_returnsEmpty() {
        var dto = new TumLivePlaylistDTO(null); // stream == null

        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("courseXYZ"), eq("999")).retrieve().body(any(ParameterizedTypeReference.class))).thenReturn(dto);

        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/w/courseXYZ/999");
        assertThat(result).isEmpty();
    }

    @Test
    void getTumLivePlaylistLink_apiThrows_returnsEmpty() {
        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("abc-course"), eq("12345")).retrieve().body(any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("boom"));

        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/w/abc-course/12345");
        assertThat(result).isEmpty();
    }
}
