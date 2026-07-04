package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;

class PyrisStruggleSignalDTOTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesToCamelCaseWireShape() throws Exception {
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(540, "FM", List.of("FM", "STATE"), 0.72, "armed", false, false),
                List.of(new PyrisStruggleSignalDTO.TickDTO(520, 0.5), new PyrisStruggleSignalDTO.TickDTO(530, 0.6)),
                List.of(new PyrisStruggleSignalDTO.ComponentDTO("feedbackViewing", 0.8)), 540);
        JsonNode node = mapper.valueToTree(signal);
        assertThat(node.get("alert").get("tSessionS").asInt()).isEqualTo(540);
        assertThat(node.get("alert").get("primaryBoundary").asText()).isEqualTo("FM");
        assertThat(node.get("alert").get("inWarmup").asBoolean()).isFalse();
        assertThat(node.get("trajectory")).hasSize(2);
        assertThat(node.get("trajectory").get(1).get("s").asDouble()).isEqualTo(0.6);
        assertThat(node.get("dominantComponents").get(0).get("name").asText()).isEqualTo("feedbackViewing");
        assertThat(node.get("sessionSeconds").asInt()).isEqualTo(540);
    }

    @Test
    void emptyCollectionsAreNotDroppedFromWire() throws Exception {
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "STATE", List.of("STATE"), 0.6, "armed", true, false), List.of(), List.of(), 1);
        JsonNode node = mapper.valueToTree(signal);
        assertThat(node.has("trajectory")).isTrue();
        assertThat(node.has("dominantComponents")).isTrue();
    }

    @Test
    void deserializesInboundClientPayload() throws Exception {
        String json = """
                {"alert":{"tSessionS":540,"primaryBoundary":"FM","boundaryTypes":["FM"],"severity":0.7,"path":"armed","inWarmup":false,"inGrace":false},
                 "trajectory":[{"t":520,"s":0.5}],"dominantComponents":[{"name":"typing","value":0.4}],"sessionSeconds":540}""";
        var signal = mapper.readValue(json, PyrisStruggleSignalDTO.class);
        assertThat(signal.alert().primaryBoundary()).isEqualTo("FM");
        assertThat(signal.dominantComponents().get(0).name()).isEqualTo("typing");
    }

    @Test
    void tpsDiscreteAlertPassesThroughUnchanged() throws Exception {
        // The discrete test-stagnation path sends primaryBoundary=TPS with path=discrete. Artemis must
        // forward these values opaquely (no enum, no branching); Pyris validates them. This pins the
        // pass-through property the cross-repo contract relies on.
        String json = """
                {"alert":{"tSessionS":540,"primaryBoundary":"TPS","boundaryTypes":["TPS"],"severity":0.41,"path":"discrete","inWarmup":true,"inGrace":false},
                 "trajectory":[{"t":530,"s":0.4}],"dominantComponents":[{"name":"typing","value":0.3}],"sessionSeconds":540}""";
        var signal = mapper.readValue(json, PyrisStruggleSignalDTO.class);
        assertThat(signal.alert().primaryBoundary()).isEqualTo("TPS");
        assertThat(signal.alert().boundaryTypes()).containsExactly("TPS");
        assertThat(signal.alert().path()).isEqualTo("discrete");

        JsonNode node = mapper.valueToTree(signal);
        assertThat(node.get("alert").get("primaryBoundary").asText()).isEqualTo("TPS");
        assertThat(node.get("alert").get("path").asText()).isEqualTo("discrete");
        assertThat(node.get("alert").get("inWarmup").asBoolean()).isTrue();
    }
}
