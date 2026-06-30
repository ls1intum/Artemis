package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;

class PyrisStruggleInterventionExecutionDTOTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serializesTopLevelSettingsInitialStagesAndSignal() throws Exception {
        var settings = new PyrisPipelineExecutionSettingsDTO("job-1", null, "http://localhost:8080", "default");
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), List.of(), 1);
        var stage = new PyrisStageDTO("Init", 10, PyrisStageState.NOT_STARTED, null, false, null);
        // Non-empty initialStages so NON_EMPTY keeps it: this proves it is hoisted as a top-level sibling of
        // settings (not nested). empty chatHistory + null exercise are dropped by NON_EMPTY (Pyris defaults them).
        var dto = new PyrisStruggleInterventionPipelineExecutionDTO(signal, null, null, List.of(), null, null, settings, List.of(stage), null, null);
        JsonNode node = mapper.valueToTree(dto);
        assertThat(node.get("settings").get("authenticationToken").asText()).isEqualTo("job-1");
        assertThat(node.get("initialStages")).hasSize(1);        // top-level sibling of settings (hoisted, not nested)
        assertThat(node.get("struggleSignal").get("alert").get("primaryBoundary").asText()).isEqualTo("FM");
        assertThat(node.has("chatHistory")).isFalse();           // NON_EMPTY drops the empty list (Pyris defaults it)
        assertThat(node.has("programmingExercise")).isFalse();   // NON_EMPTY drops null @Nullable fields
    }

    @Test
    void serializesIntentAndEpisodeWithEmptyHintsPresent() throws Exception {
        var settings = new PyrisPipelineExecutionSettingsDTO("job-2", null, "http://localhost:8080", "default");
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), List.of(), 1);
        var episode = new StruggleEpisodeDTO("ep-1", true, List.of());
        var stage = new PyrisStageDTO("Init", 10, PyrisStageState.NOT_STARTED, null, false, null);
        var dto = new PyrisStruggleInterventionPipelineExecutionDTO(signal, null, null, List.of(), null, null, settings, List.of(stage), "decide", episode);
        JsonNode node = mapper.valueToTree(dto);
        assertThat(node.get("intent").asText()).isEqualTo("decide");
        assertThat(node.has("episode")).isTrue();
        assertThat(node.get("episode").get("episodeId").asText()).isEqualTo("ep-1");
        assertThat(node.get("episode").get("isNew").asBoolean()).isTrue();
        // hints MUST be present and empty -- NON_EMPTY would have dropped hints:[], breaking the Pyris contract
        assertThat(node.get("episode").has("hints")).isTrue();
        assertThat(node.get("episode").get("hints").isArray()).isTrue();
        assertThat(node.get("episode").get("hints").size()).isEqualTo(0);
    }

    @Test
    void nullIntentAndEpisodeAreOmittedByNonEmpty() throws Exception {
        var settings = new PyrisPipelineExecutionSettingsDTO("job-3", null, "http://localhost:8080", "default");
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), List.of(), 1);
        var stage = new PyrisStageDTO("Init", 10, PyrisStageState.NOT_STARTED, null, false, null);
        var dto = new PyrisStruggleInterventionPipelineExecutionDTO(signal, null, null, List.of(), null, null, settings, List.of(stage), null, null);
        JsonNode node = mapper.valueToTree(dto);
        assertThat(node.has("intent")).isFalse();
        assertThat(node.has("episode")).isFalse();
    }
}
