package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.lecture.dto.TumLivePlaylistDTO;
import de.tum.cit.aet.artemis.lecture.dto.TumLivePlaylistDTO.StreamDTO;
import de.tum.cit.aet.artemis.lecture.service.TumLiveService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class TumLiveServiceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "tumliveservicetest";

    @Autowired
    private TumLiveService tumLiveService;

    private RestClient restClient; // deep-stubbed

    @BeforeEach
    void setUp() {
        // Deep stubs: restClient.get().uri(...).retrieve().body(...)
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        // Swap the private RestClient built in the constructor
        ReflectionTestUtils.setField(tumLiveService, "restClient", restClient);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void getTumLivePlaylistLink_validUrl_returnsPlaylist() {
        var dtoType = new ParameterizedTypeReference<TumLivePlaylistDTO>() {
        };
        var playlist = "https://cdn.tum.live/playlist_12345.m3u8";
        var dto = new TumLivePlaylistDTO(new StreamDTO(playlist));

        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("abc-course"), eq("12345")).retrieve().body(eq(dtoType))).thenReturn(dto);

        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/w/abc-course/12345");

        assertThat(result).isPresent().contains(playlist);
        verify(restClient.get()).uri(eq("/streams/{courseSlug}/{streamId}"), eq("abc-course"), eq("12345"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void getTumLivePlaylistLink_notTumLiveHost_returnsEmpty() {
        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://example.com/some/path");
        assertThat(result).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void getTumLivePlaylistLink_invalidPath_returnsEmpty() {
        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/invalid/path");
        assertThat(result).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void getTumLivePlaylistLink_apiReturnsNoPlaylist_returnsEmpty() {
        var dtoType = new ParameterizedTypeReference<TumLivePlaylistDTO>() {
        };
        var dto = new TumLivePlaylistDTO(null); // stream == null

        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("courseXYZ"), eq("999")).retrieve().body(eq(dtoType))).thenReturn(dto);

        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/w/courseXYZ/999");
        assertThat(result).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void getTumLivePlaylistLink_apiThrows_returnsEmpty() {
        var dtoType = new ParameterizedTypeReference<TumLivePlaylistDTO>() {
        };
        when(restClient.get().uri(eq("/streams/{courseSlug}/{streamId}"), eq("abc-course"), eq("12345")).retrieve().body(eq(dtoType))).thenThrow(new RuntimeException("boom"));

        Optional<String> result = tumLiveService.getTumLivePlaylistLink("https://live.rbg.tum.de/w/abc-course/12345");
        assertThat(result).isEmpty();
    }
}
