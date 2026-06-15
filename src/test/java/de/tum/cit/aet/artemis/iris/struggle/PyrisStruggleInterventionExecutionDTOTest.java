package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;

class PyrisStruggleInterventionExecutionDTOTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serializesTopLevelSettingsInitialStagesAndSignal() throws Exception {
        var settings = new PyrisPipelineExecutionSettingsDTO("job-1", null, "http://localhost:8080", "default");
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.Alert(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), List.of(), 1);
        var dto = new PyrisStruggleInterventionPipelineExecutionDTO(signal, null, null, List.of(), null, null, settings, List.of());
        JsonNode node = mapper.valueToTree(dto);
        assertThat(node.get("settings").get("authenticationToken").asText()).isEqualTo("job-1");
        assertThat(node.has("initialStages")).isTrue();          // top-level sibling of settings
        assertThat(node.get("struggleSignal").get("alert").get("primaryBoundary").asText()).isEqualTo("FM");
    }
}
