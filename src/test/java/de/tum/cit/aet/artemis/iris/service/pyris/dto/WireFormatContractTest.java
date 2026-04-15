package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

/**
 * Pins the Pyris wire format contract:
 * <ul>
 * <li>Outbound webhook uses camelCase {@code videoSourceType}.</li>
 * <li>Inbound status update reads snake_case {@code error_code}.</li>
 * <li>Inbound status update silently ignores camelCase {@code errorCode} (unknown field), matching Spring Boot's default mapper config.</li>
 * </ul>
 */
class WireFormatContractTest {

    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
    void inboundStatusUpdateRejectsCamelCaseErrorCode() throws Exception {
        String wire = "{\"result\":\"error\",\"stages\":[],\"jobId\":7,\"errorCode\":\"YOUTUBE_PRIVATE\"}";
        // Spring Boot's autoconfigured mapper has FAIL_ON_UNKNOWN_PROPERTIES=false, so camelCase "errorCode" is silently
        // ignored and errorCode() returns null — this test mirrors that production behavior and documents the risk:
        // if Pyris accidentally sends "errorCode" instead of "error_code", we will silently see null.
        var dto = mapper.readValue(wire, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.errorCode()).isNull();
    }
}
