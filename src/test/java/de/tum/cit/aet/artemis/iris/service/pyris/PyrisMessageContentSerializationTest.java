package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisJsonMessageContentDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageContentBaseDTO;

/**
 * Pins the Jackson round-trip contract of {@link PyrisJsonMessageContentDTO}.
 * <p>
 * The DTO is only ever serialized in production (Artemis sends a chat pipeline request to Pyris), but the request body
 * is deserialized back into the DTO by the test mock (and potentially by future consumers). Because {@code jsonContent}
 * is written as raw JSON via {@code @JsonRawValue}, a matching deserializer is required — otherwise Jackson tries to read
 * the embedded JSON object into a {@link String} and fails with "Cannot deserialize value of type String from Object
 * value". That asymmetry broke {@code PyrisEventSystemIntegrationTest.testBuildFailedFallsBackToCourseSessionAndApplies
 * ExerciseContext} once the Iris context-switch feature started putting {@code json} content messages into the chat
 * history that is round-tripped through the mock.
 */
class PyrisMessageContentSerializationTest {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    void jsonMessageContentIsSerializedAsRawJsonNotAQuotedString() throws Exception {
        var content = new PyrisJsonMessageContentDTO("{\"context\":\"exercise\",\"id\":42}");

        String json = mapper.writeValueAsString((PyrisMessageContentBaseDTO) content);

        // @JsonRawValue embeds the value as a JSON object, not an escaped/quoted string.
        assertThat(json).contains("\"jsonContent\":{").contains("\"context\":\"exercise\"").doesNotContain("\\\"context\\\"");
    }

    @Test
    void jsonMessageContentRoundTripsThroughThePolymorphicBase() throws Exception {
        var original = new PyrisJsonMessageContentDTO("{\"context\":\"exercise\",\"id\":42}");
        String json = mapper.writeValueAsString((PyrisMessageContentBaseDTO) original);

        var deserialized = mapper.readValue(json, PyrisMessageContentBaseDTO.class);

        assertThat(deserialized).isInstanceOf(PyrisJsonMessageContentDTO.class);
        String roundTripped = ((PyrisJsonMessageContentDTO) deserialized).jsonContent();
        // The raw JSON is read back into a semantically-equal JSON string (whitespace/key-order independent comparison).
        assertThat(mapper.readTree(roundTripped)).isEqualTo(mapper.readTree(original.jsonContent()));
    }
}
