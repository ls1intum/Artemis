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
        // NON_EMPTY serialization contract: a push without session/message ids (e.g. a partial payload) omits both.
        var event = new StruggleInterventionEventDTO(42, "decide", "ambient", "Re-check the logic.", null, null, null, null, null, 0.7, null, null, null, null, null);
        JsonNode node = mapper.valueToTree(event);
        assertThat(node.get("exerciseId").asLong()).isEqualTo(42);
        assertThat(node.get("kind").asText()).isEqualTo("decide");
        assertThat(node.get("action").asText()).isEqualTo("ambient");
        assertThat(node.get("message").asText()).contains("logic");
        assertThat(node.has("sessionId")).isFalse();
        assertThat(node.has("messageId")).isFalse();
        assertThat(node.has("episodeId")).isFalse();
        // confidence is forwarded for the client eval log (§12) on both ambient and active.
        assertThat(node.get("confidence").asDouble()).isEqualTo(0.7);
    }

    @Test
    void eventCarriesSessionIdMessageIdAndConfidence() throws Exception {
        // Active event carries sessionId + messageId of the saved message, and the episodeId for slot correlation.
        var event = new StruggleInterventionEventDTO(42, "decide", "active", null, 99L, 555L, null, null, null, 0.81, "ep-abc", null, null, null, null);
        JsonNode node = mapper.valueToTree(event);
        assertThat(node.get("kind").asText()).isEqualTo("decide");
        assertThat(node.get("sessionId").asLong()).isEqualTo(99);
        assertThat(node.get("messageId").asLong()).isEqualTo(555);
        assertThat(node.get("confidence").asDouble()).isEqualTo(0.81);
        assertThat(node.get("episodeId").asText()).isEqualTo("ep-abc");
    }

    @Test
    void silentEventOmitsSessionMessageAndEpisodeWhenNull() throws Exception {
        // Silent completion frames carry kind + action, but omit optional fields when null.
        var event = new StruggleInterventionEventDTO(42, "decide", "silent", null, null, null, null, null, null, null, null, null, null, null, null);
        JsonNode node = mapper.valueToTree(event);
        assertThat(node.get("kind").asText()).isEqualTo("decide");
        assertThat(node.get("action").asText()).isEqualTo("silent");
        assertThat(node.has("message")).isFalse();
        assertThat(node.has("sessionId")).isFalse();
        assertThat(node.has("messageId")).isFalse();
        assertThat(node.has("episodeId")).isFalse();
    }

    @Test
    void confirmCloseFieldsRoundTripCorrectly() throws Exception {
        // The confirm_close payload fields set to non-null values; serialized JSON must carry each one.
        var event = new StruggleInterventionEventDTO(42, "confirm_close", null, null, null, 77L, null, null, null, null, "ep-rt", true, "Nice work, that is resolved.", "Resolved",
                null);
        JsonNode node = mapper.valueToTree(event);

        assertThat(node.get("resolved").asBoolean()).isTrue();
        assertThat(node.get("closingSentence").asText()).isEqualTo("Nice work, that is resolved.");
        assertThat(node.get("episodeLabel").asText()).isEqualTo("Resolved");

        // CRITICAL: resolved=false must appear explicitly on the wire so the client receives it.
        // NON_EMPTY must NOT omit Boolean false (only null is omitted).
        var falseEvent = new StruggleInterventionEventDTO(42, "confirm_close", null, null, null, null, null, null, null, null, "ep-rt", false, null, null, null);
        JsonNode falseNode = mapper.valueToTree(falseEvent);
        assertThat(falseNode.hasNonNull("resolved")).isTrue();
        assertThat(falseNode.get("resolved").asBoolean()).isFalse();
    }

    @Test
    void acceptedDtoCarriesJobIdOrNull() {
        assertThat(new StruggleInterventionAcceptedDTO(true, false, 42, "tok").jobId()).isEqualTo("tok");
        assertThat(new StruggleInterventionAcceptedDTO(false, true, 42, null).courseDisabled()).isTrue();
    }
}
