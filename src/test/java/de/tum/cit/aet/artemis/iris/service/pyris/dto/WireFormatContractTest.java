package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

/**
 * Pins the Pyris wire format contract:
 * <ul>
 * <li>Outbound webhook uses camelCase {@code videoSourceType}.</li>
 * <li>Inbound status update reads snake_case {@code error_code}.</li>
 * <li>Inbound status update does NOT accept camelCase {@code errorCode}.</li>
 * </ul>
 */
class WireFormatContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void outboundWebhookUsesCamelCaseVideoSourceType() throws Exception {
        var dto = new PyrisLectureUnitWebhookDTO("", 0, null, 1L, "name", 2L, "lecture", 3L, "course", "desc", "url", "https://x", VideoSourceType.YOUTUBE);
        String json = mapper.writeValueAsString(dto);
        assertThat(json).contains("\"videoSourceType\":\"YOUTUBE\"");
        assertThat(json).doesNotContain("video_source_type");
    }

    @Test
    void inboundStatusUpdateReadsSnakeCaseErrorCode() throws Exception {
        String json = "{\"result\":\"error\",\"stages\":[],\"jobId\":7,\"error_code\":\"YOUTUBE_PRIVATE\"}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.errorCode()).isEqualTo("YOUTUBE_PRIVATE");
    }

    @Test
    void inboundStatusUpdateRejectsCamelCaseErrorCode() {
        String json = "{\"result\":\"error\",\"stages\":[],\"jobId\":7,\"errorCode\":\"YOUTUBE_PRIVATE\"}";
        assertThatThrownBy(() -> mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class)).isInstanceOf(UnrecognizedPropertyException.class)
                .hasMessageContaining("errorCode");
    }
}
