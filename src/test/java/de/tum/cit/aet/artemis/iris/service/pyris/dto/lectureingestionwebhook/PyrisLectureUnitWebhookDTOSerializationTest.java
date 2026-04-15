package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

class PyrisLectureUnitWebhookDTOSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void omitsVideoSourceTypeWhenNull() throws Exception {
        var dto = new PyrisLectureUnitWebhookDTO("", 0, null, 1L, "n", 2L, "l", 3L, "c", "d", "url", "https://x", null);
        String json = mapper.writeValueAsString(dto);
        assertThat(json).doesNotContain("videoSourceType");
    }

    @Test
    void includesVideoSourceTypeWhenPresent() throws Exception {
        var dto = new PyrisLectureUnitWebhookDTO("", 0, null, 1L, "n", 2L, "l", 3L, "c", "d", "url", "https://x", VideoSourceType.YOUTUBE);
        String json = mapper.writeValueAsString(dto);
        assertThat(json).contains("\"videoSourceType\":\"YOUTUBE\"");
    }
}
