package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;

class PyrisLectureIngestionStatusUpdateDTOSerializationTest {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    void deserializesErrorCodeFromErrorObject() throws Exception {
        String json = "{\"result\":\"done\",\"runState\":\"FAILED\",\"error\":{\"code\":\"YOUTUBE_PRIVATE\"},\"jobId\":42}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.error().code()).isEqualTo("YOUTUBE_PRIVATE");
    }

    @Test
    void deserializesDisplayPageNumbers() throws Exception {
        String json = "{\"result\":\"done\",\"runState\":\"FINISHED\",\"jobId\":42,\"displayPageNumbers\":[1,2,-1]}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.displayPageNumbers()).containsExactly(1, 2, -1);
    }
}
