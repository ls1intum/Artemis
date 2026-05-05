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
    void deserializesSlidePageNumberMap() throws Exception {
        String json = "{\"result\":\"done\",\"stages\":[],\"jobId\":42,\"slide_page_number_map\":{\"0\":1,\"1\":2,\"2\":-1,\"3\":4}}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.slidePageNumberMap()).isNotNull().hasSize(4).containsEntry("0", 1).containsEntry("1", 2).containsEntry("2", -1).containsEntry("3", 4);
    }

    @Test
    void deserializesEmptySlidePageNumberMap() throws Exception {
        String json = "{\"result\":\"done\",\"stages\":[],\"jobId\":42,\"slide_page_number_map\":{}}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.slidePageNumberMap()).isNotNull().isEmpty();
    }

    @Test
    void deserializesMissingSlidePageNumberMap() throws Exception {
        String json = "{\"result\":\"done\",\"stages\":[],\"jobId\":42}";
        var dto = mapper.readValue(json, PyrisLectureIngestionStatusUpdateDTO.class);
        assertThat(dto.slidePageNumberMap()).isNull();
    }
}
