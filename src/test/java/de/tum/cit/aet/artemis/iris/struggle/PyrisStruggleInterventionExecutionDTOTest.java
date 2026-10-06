package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;

class PyrisStruggleInterventionExecutionDTOTest {

    private final JsonMapper mapper = JsonObjectMapper.get();

    @Test
    void serializesTopLevelSettingsAndSignal() throws Exception {
        var settings = new PyrisPipelineExecutionSettingsDTO("job-1", null, "http://localhost:8080", "default", "moderate");
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), 1);
        // settings is hoisted as a top-level sibling (not nested).
        // empty chatHistory + null exercise are dropped by NON_EMPTY (Pyris defaults them).
        var dto = new PyrisStruggleInterventionPipelineExecutionDTO(signal, null, null, List.of(), null, null, settings, null, null, null);
        JsonNode node = mapper.valueToTree(dto);
        assertThat(node.get("settings").get("authenticationToken").asString()).isEqualTo("job-1");
        assertThat(node.get("struggleSignal").get("alert").get("primaryBoundary").asString()).isEqualTo("FM");
        assertThat(node.has("chatHistory")).isFalse();           // NON_EMPTY drops the empty list (Pyris defaults it)
        assertThat(node.has("programmingExercise")).isFalse();   // NON_EMPTY drops null @Nullable fields
    }

    @Test
    void serializesIntentAndEpisodeWithEmptyHintsPresent() throws Exception {
        var settings = new PyrisPipelineExecutionSettingsDTO("job-2", null, "http://localhost:8080", "default", "moderate");
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), 1);
        var episode = new StruggleEpisodeDTO("ep-1", true, List.of());
        var dto = new PyrisStruggleInterventionPipelineExecutionDTO(signal, null, null, List.of(), null, null, settings, "decide", episode, "push");
        JsonNode node = mapper.valueToTree(dto);
        assertThat(node.get("intent").asString()).isEqualTo("decide");
        // proactivityMode serializes snake_case for the Pyris boundary (@JsonProperty("proactivity_mode"))
        assertThat(node.get("proactivity_mode").asString()).isEqualTo("push");
        assertThat(node.has("episode")).isTrue();
        assertThat(node.get("episode").get("episodeId").asString()).isEqualTo("ep-1");
        assertThat(node.get("episode").get("isNew").asBoolean()).isTrue();
        // hints MUST be present and empty -- NON_EMPTY would have dropped hints:[], breaking the Pyris contract
        assertThat(node.get("episode").has("hints")).isTrue();
        assertThat(node.get("episode").get("hints").isArray()).isTrue();
        assertThat(node.get("episode").get("hints").size()).isEqualTo(0);
    }

    @Test
    void nullIntentAndEpisodeAreOmittedByNonEmpty() throws Exception {
        var settings = new PyrisPipelineExecutionSettingsDTO("job-3", null, "http://localhost:8080", "default", "moderate");
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), 1);
        var dto = new PyrisStruggleInterventionPipelineExecutionDTO(signal, null, null, List.of(), null, null, settings, null, null, null);
        JsonNode node = mapper.valueToTree(dto);
        assertThat(node.has("intent")).isFalse();
        assertThat(node.has("episode")).isFalse();
        assertThat(node.has("proactivity_mode")).isFalse();
    }
}
