package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

class PyrisLectureUnitWebhookDTOSerializationTest {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    void omitsVideoSourceTypeWhenNull() throws Exception {
        var dto = new PyrisLectureUnitWebhookDTO("", 0, null, 1L, "n", 2L, "l", 3L, "c", "d", "url", "https://x", null, null);
        String json = mapper.writeValueAsString(dto);
        assertThat(json).doesNotContain("videoSourceType");
    }

    @Test
    void includesVideoSourceTypeWhenPresent() throws Exception {
        var dto = new PyrisLectureUnitWebhookDTO("", 0, null, 1L, "n", 2L, "l", 3L, "c", "d", "url", "https://x", VideoSourceType.YOUTUBE, null);
        String json = mapper.writeValueAsString(dto);
        assertThat(json).contains("\"videoSourceType\":\"YOUTUBE\"");
    }

    @Test
    void omitsContentFingerprintWhenNull() throws Exception {
        var dto = new PyrisLectureUnitWebhookDTO("", 0, null, 1L, "n", 2L, "l", 3L, "c", "d", "url", "https://x", null, null);
        String json = mapper.writeValueAsString(dto);
        assertThat(json).doesNotContain("contentFingerprint");
    }

    @Test
    void includesContentFingerprintWhenPresent() throws Exception {
        var dto = new PyrisLectureUnitWebhookDTO("", 0, null, 1L, "n", 2L, "l", 3L, "c", "d", "url", "https://x", null, "v1:abc");
        String json = mapper.writeValueAsString(dto);
        assertThat(json).contains("\"contentFingerprint\":\"v1:abc\"");
    }
}
