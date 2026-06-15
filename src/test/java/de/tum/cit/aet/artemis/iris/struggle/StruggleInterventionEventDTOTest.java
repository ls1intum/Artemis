package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionAcceptedDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionEventDTO;

class StruggleInterventionEventDTOTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ambientEventOmitsSessionIdButCarriesConfidence() throws Exception {
        var event = new StruggleInterventionEventDTO(42, "ambient", "Re-check the logic.", null, 0.7);
        JsonNode node = mapper.valueToTree(event);
        assertThat(node.get("exerciseId").asLong()).isEqualTo(42);
        assertThat(node.get("action").asText()).isEqualTo("ambient");
        assertThat(node.get("message").asText()).contains("logic");
        assertThat(node.hasNonNull("sessionId")).isFalse();
        // confidence is forwarded for the client eval log (§12) on BOTH ambient and active — ambient is the only
        // place ambient confidence is observable client-side (it is never persisted as an LLM message).
        assertThat(node.get("confidence").asDouble()).isEqualTo(0.7);
    }

    @Test
    void activeEventCarriesSessionIdAndConfidence() throws Exception {
        var event = new StruggleInterventionEventDTO(42, "active", null, 99L, 0.81);
        JsonNode node = mapper.valueToTree(event);
        assertThat(node.get("sessionId").asLong()).isEqualTo(99);
        assertThat(node.get("confidence").asDouble()).isEqualTo(0.81);
    }

    @Test
    void acceptedDtoCarriesJobIdOrNull() {
        assertThat(new StruggleInterventionAcceptedDTO(true, 42, "tok").jobId()).isEqualTo("tok");
        assertThat(new StruggleInterventionAcceptedDTO(false, 42, null).accepted()).isFalse();
    }
}
