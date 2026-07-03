package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;

class PyrisChatStatusUpdateDTOTest {

    private final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Test
    void deserializesPartialFields() throws JsonProcessingException {
        String json = """
                {
                    "stages": [],
                    "partialResult": "Hello",
                    "partialSeq": 7
                }
                """;

        var dto = objectMapper.readValue(json, PyrisChatStatusUpdateDTO.class);

        assertThat(dto.partialResult()).isEqualTo("Hello");
        assertThat(dto.partialSeq()).isEqualTo(7);
    }

    @Test
    void deserializesWithoutPartialFields() throws JsonProcessingException {
        String json = """
                {
                    "stages": []
                }
                """;

        var dto = objectMapper.readValue(json, PyrisChatStatusUpdateDTO.class);

        assertThat(dto.partialResult()).isNull();
        assertThat(dto.partialSeq()).isNull();
    }
}
