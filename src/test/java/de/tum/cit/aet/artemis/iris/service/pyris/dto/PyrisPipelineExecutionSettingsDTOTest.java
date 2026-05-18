package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class PyrisPipelineExecutionSettingsDTOTest {

    private final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Test
    void carriesSupportLevel() {
        var dto = new PyrisPipelineExecutionSettingsDTO("token", null, "https://artemis.example", "default", "high");

        assertThat(dto.supportLevel()).isEqualTo("high");
    }

    @Test
    void serializesSupportLevel() throws JsonProcessingException {
        var dto = new PyrisPipelineExecutionSettingsDTO("token", null, "https://artemis.example", "default", "low");

        String serialized = objectMapper.writeValueAsString(dto);

        assertThat(serialized).contains("\"supportLevel\":\"low\"");
    }
}
