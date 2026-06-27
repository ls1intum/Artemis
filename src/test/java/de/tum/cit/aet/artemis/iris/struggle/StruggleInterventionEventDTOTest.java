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
    void nullSessionAndMessageIdsAreOmitted() throws Exception {
        // NON_NULL serialization contract: a push without session/message ids (e.g. a partial payload) omits both.
        var event = new StruggleInterventionEventDTO(42, "ambient", "Re-check the logic.", null, null, null, null, null, 0.7);
        JsonNode node = mapper.valueToTree(event);
        assertThat(node.get("exerciseId").asLong()).isEqualTo(42);
        assertThat(node.get("action").asText()).isEqualTo("ambient");
        assertThat(node.get("message").asText()).contains("logic");
        assertThat(node.hasNonNull("sessionId")).isFalse();
        assertThat(node.hasNonNull("messageId")).isFalse();
        // confidence is forwarded for the client eval log (§12) on both ambient and active.
        assertThat(node.get("confidence").asDouble()).isEqualTo(0.7);
    }

    @Test
    void eventCarriesSessionIdMessageIdAndConfidence() throws Exception {
        // After unify-persistence (§7) both ambient and active carry sessionId + messageId of the saved message.
        var event = new StruggleInterventionEventDTO(42, "active", null, 99L, 555L, null, null, null, 0.81);
        JsonNode node = mapper.valueToTree(event);
        assertThat(node.get("sessionId").asLong()).isEqualTo(99);
        assertThat(node.get("messageId").asLong()).isEqualTo(555);
        assertThat(node.get("confidence").asDouble()).isEqualTo(0.81);
    }

    @Test
    void acceptedDtoCarriesJobIdOrNull() {
        assertThat(new StruggleInterventionAcceptedDTO(true, false, 42, "tok").jobId()).isEqualTo("tok");
        assertThat(new StruggleInterventionAcceptedDTO(false, true, 42, null).courseDisabled()).isTrue();
    }
}
