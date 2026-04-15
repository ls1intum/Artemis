package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class PyrisLectureIngestionStatusUpdateDTOSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesSnakeCaseErrorCode() throws Exception {
        String json = "{\"result\":\"done\",\"stages\":[],\"jobId\":42,\"error_code\":\"YOUTUBE_PRIVATE\"}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.errorCode()).isEqualTo("YOUTUBE_PRIVATE");
    }
}
