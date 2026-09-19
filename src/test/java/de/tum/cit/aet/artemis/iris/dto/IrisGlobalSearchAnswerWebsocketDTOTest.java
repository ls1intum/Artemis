package de.tum.cit.aet.artemis.iris.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class IrisGlobalSearchAnswerWebsocketDTOTest {

    @Test
    void reproducesEmptyPartialResultBeingDroppedByNonEmptyInclusion() {
        var dto = new IrisGlobalSearchAnswerWebsocketDTO("run-1", true, null, null, "", 7, null);

        String json = JsonObjectMapper.get().writeValueAsString(dto);

        assertThat(json).as("empty partialResult must survive serialization as the client's clear-stale-draft signal").contains("\"partialResult\"");
    }
}
