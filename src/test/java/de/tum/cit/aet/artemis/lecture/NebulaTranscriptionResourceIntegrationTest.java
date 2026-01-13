package de.tum.cit.aet.artemis.lecture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration tests for the Nebula transcription resource.
 * Tests for starting transcriptions have been removed as transcription
 * is now triggered automatically by the LectureContentProcessingService.
 * Cancel transcription tests require additional setup and are covered elsewhere.
 */
class NebulaTranscriptionResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "nebulatranscriptionresource";

    @Autowired
    private MockMvc restNebulaTranscriptionMockMvc;

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void getTumLivePlaylist_notTumLiveUrl_notFound() throws Exception {
        // Mock TUM Live service to return empty for non-TUM-Live URLs
        when(tumLiveService.getTumLivePlaylistLink(anyString())).thenReturn(java.util.Optional.empty());

        // Non-TUM-Live URLs should return 404
        restNebulaTranscriptionMockMvc.perform(get("/api/nebula/video-utils/tum-live-playlist").param("url", "https://youtube.com/watch?v=123")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void getTumLivePlaylist_invalidUrl_notFound() throws Exception {
        // Mock TUM Live service to return empty for invalid URL formats
        when(tumLiveService.getTumLivePlaylistLink(anyString())).thenReturn(java.util.Optional.empty());

        // Invalid TUM Live URL format should return 404
        restNebulaTranscriptionMockMvc.perform(get("/api/nebula/video-utils/tum-live-playlist").param("url", "https://tum.live/invalid-format")).andExpect(status().isNotFound());
    }
}
