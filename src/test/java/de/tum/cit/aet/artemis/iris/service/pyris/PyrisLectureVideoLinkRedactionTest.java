package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitMetadataWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

class PyrisLectureVideoLinkRedactionTest {

    private static final String SIGNED_PLAYLIST = "https://edge.example/vod/42/COMB/playlist.m3u8?jwt=secret-token";

    @Test
    void ingestionPayloadDoesNotPrintTheSignedPlaylist() {
        var dto = new PyrisLectureUnitWebhookDTO("JVBERi0x", 1, null, 3, "Unit", 2, "Lecture", 1, "Course", "", "", SIGNED_PLAYLIST, VideoSourceType.TUM_LIVE, "fingerprint");

        assertThat(dto.toString()).doesNotContain("secret-token").doesNotContain("JVBERi0x").contains("videoLink=[REDACTED]").contains("lectureUnitId=3");
        assertThat(dto.videoLink()).isEqualTo(SIGNED_PLAYLIST);
    }

    @Test
    void metadataPayloadDoesNotPrintTheSignedPlaylist() {
        var dto = new PyrisLectureUnitMetadataWebhookDTO(3L, "Unit", "", 2L, "Lecture", 1L, "Course", "", SIGNED_PLAYLIST, "https://artemis.example");

        assertThat(dto.toString()).doesNotContain("secret-token").contains("videoLink=[REDACTED]").contains("lectureUnitId=3");
    }
}
