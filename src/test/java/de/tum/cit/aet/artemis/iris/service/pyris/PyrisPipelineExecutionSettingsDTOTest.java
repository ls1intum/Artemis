package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

class PyrisPipelineExecutionSettingsDTOTest {

    private final JsonMapper objectMapper = JsonObjectMapper.get();

    @Test
    void carriesSupportLevel() {
        var dto = new PyrisPipelineExecutionSettingsDTO("token", null, "https://artemis.example", "default", "high");

        assertThat(dto.supportLevel()).isEqualTo("high");
    }

    @Test
    void serializesSupportLevel() throws JacksonException {
        var dto = new PyrisPipelineExecutionSettingsDTO("token", null, "https://artemis.example", "default", "low");

        String serialized = objectMapper.writeValueAsString(dto);

        assertThat(serialized).contains("\"supportLevel\":\"low\"");
    }

    @Test
    void serializesStreamResponseWhenEnabled() throws JacksonException {
        var dto = new PyrisPipelineExecutionSettingsDTO("token", null, "https://artemis.example", "default", "low", true);

        String serialized = objectMapper.writeValueAsString(dto);

        assertThat(serialized).contains("\"streamResponse\":true");
    }

    @Test
    void omitsStreamResponseWhenUnset() throws JacksonException {
        var dto = new PyrisPipelineExecutionSettingsDTO("token", null, "https://artemis.example", "default", "low");

        String serialized = objectMapper.writeValueAsString(dto);

        assertThat(serialized).doesNotContain("streamResponse");
    }
}
