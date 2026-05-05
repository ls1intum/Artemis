package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;

class PyrisLectureIngestionStatusUpdateDTOSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesSnakeCaseErrorCode() throws Exception {
        String json = "{\"result\":\"done\",\"stages\":[],\"jobId\":42,\"error_code\":\"YOUTUBE_PRIVATE\"}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.errorCode()).isEqualTo("YOUTUBE_PRIVATE");
    }

    @Test
    void deserializesFinalResultJsonPayload() throws Exception {
        String json = "{\"result\":\"{\\\"slidePageNumberMap\\\":{\\\"1\\\":1,\\\"2\\\":2,\\\"3\\\":-1}}\",\"stages\":[],\"jobId\":42}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.result()).isEqualTo("{\"slidePageNumberMap\":{\"1\":1,\"2\":2,\"3\":-1}}");
    }
}
