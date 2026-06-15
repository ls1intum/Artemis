package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;

class PyrisStruggleInterventionStatusUpdateDTOTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesFlatPyrisCallback() throws Exception {
        String json = """
                {"result":"Have you checked the empty-list case?","action":"active","confidence":0.81,
                 "rationale":"FM boundary, feedback-viewing dominant","stages":[],"tokens":[]}""";
        var dto = mapper.readValue(json, PyrisStruggleInterventionStatusUpdateDTO.class);
        assertThat(dto.action()).isEqualTo("active");
        assertThat(dto.confidence()).isEqualTo(0.81);
        assertThat(dto.result()).isEqualTo("Have you checked the empty-list case?");
    }

    @Test
    void silentCallbackShapeAndNullStagesTokensDefaultToEmptyLists() {
        // Real silent-decision callback shape: result == null, action == "silent" — the record header is
        // (result, action, confidence, rationale, stages, tokens), so the "silent" literal MUST sit in the
        // action position. This is the exact shape Task 11's handleDecision keys on (action != null && result == null).
        var dto = new PyrisStruggleInterventionStatusUpdateDTO(null, "silent", 0.1, null, null, null);
        assertThat(dto.action()).isEqualTo("silent");
        assertThat(dto.result()).isNull();
        assertThat(dto.stages()).isEmpty();
        assertThat(dto.tokens()).isEmpty();
    }
}
