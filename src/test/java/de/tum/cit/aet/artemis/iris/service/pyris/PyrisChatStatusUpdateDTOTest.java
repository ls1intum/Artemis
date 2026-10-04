package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityKind;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;

class PyrisChatStatusUpdateDTOTest {

    private final JsonMapper objectMapper = JsonObjectMapper.get();

    @Test
    void deserializesPartialFields() throws JacksonException {
        String json = """
                {
                    "runState": "RUNNING",
                    "partialResult": "Hello",
                    "partialSeq": 7
                }
                """;

        var dto = objectMapper.readValue(json, PyrisChatStatusUpdateDTO.class);

        assertThat(dto.runState()).isEqualTo(PyrisRunState.RUNNING);
        assertThat(dto.partialResult()).isEqualTo("Hello");
        assertThat(dto.partialSeq()).isEqualTo(7);
    }

    @Test
    void deserializesActivities() throws JacksonException {
        String json = """
                {
                    "runState": "RUNNING",
                    "activities": [
                        {
                            "id": "act-1",
                            "kind": "TOOL",
                            "name": "lecture_content_retrieval",
                            "state": "FINISHED",
                            "detail": "query",
                            "result": "12 sections",
                            "durationMillis": 3100
                        }
                    ],
                    "activitySeq": 3
                }
                """;

        var dto = objectMapper.readValue(json, PyrisChatStatusUpdateDTO.class);

        assertThat(dto.runState()).isEqualTo(PyrisRunState.RUNNING);
        assertThat(dto.activities()).singleElement().satisfies(activity -> {
            assertThat(activity.kind()).isEqualTo(PyrisActivityKind.TOOL);
            assertThat(activity.state()).isEqualTo(PyrisActivityState.FINISHED);
            assertThat(activity.durationMillis()).isEqualTo(3100);
        });
        assertThat(dto.activitySeq()).isEqualTo(3);
    }

    @Test
    void deserializesAndSerializesFinalFlagWithReservedJsonName() throws JacksonException {
        String json = """
                {
                    "runState": "RUNNING",
                    "result": "Let me check first",
                    "final": false
                }
                """;

        var dto = objectMapper.readValue(json, PyrisChatStatusUpdateDTO.class);

        assertThat(dto.result()).isEqualTo("Let me check first");
        assertThat(dto.finalResult()).isFalse();

        String serialized = objectMapper.writeValueAsString(dto);

        assertThat(serialized).contains("\"final\":false");
        assertThat(serialized).doesNotContain("finalResult");
    }

    @Test
    void serializesWithoutStagesField() throws JacksonException {
        var dto = new PyrisChatStatusUpdateDTO(null, PyrisRunState.RUNNING, null, null, null, null, null, null, null, null, null, null);

        String json = objectMapper.writeValueAsString(dto);

        assertThat(json).contains("\"runState\":\"RUNNING\"");
        assertThat(json).doesNotContain("stages");
    }
}
