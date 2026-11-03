package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;

import de.tum.cit.aet.artemis.core.dto.agent.AgentProgrammingTimelinePatchDTO;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class AgentProgrammingTimelinePatchDeserializerTest {

    @Test
    void rejectsMisspelledTimelineFieldsInsteadOfSilentlyKeepingTheOldValue() {
        assertThatExceptionOfType(JacksonException.class)
                .isThrownBy(() -> JsonObjectMapper.get().readValue("{\"id\":42,\"dueDat\":\"2027-01-01T12:00:00Z\"}", AgentProgrammingTimelinePatchDTO.class))
                .withMessageContaining("dueDat");
    }
}
