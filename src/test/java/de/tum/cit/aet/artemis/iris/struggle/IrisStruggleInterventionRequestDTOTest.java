package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.iris.dto.IrisStruggleInterventionRequestDTO;

class IrisStruggleInterventionRequestDTOTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesRequest() throws Exception {
        String json = """
                {"struggleSignal":{"alert":{"tSessionS":540,"primaryBoundary":"FM","boundaryTypes":["FM"],"severity":0.7,"path":"armed","inWarmup":false,"inGrace":false},
                   "trajectory":[],"dominantComponents":[],"sessionSeconds":540},
                 "uncommittedFiles":{"src/A.java":"class A {}"}}""";
        var dto = mapper.readValue(json, IrisStruggleInterventionRequestDTO.class);
        assertThat(dto.struggleSignal().alert().primaryBoundary()).isEqualTo("FM");
        assertThat(dto.uncommittedFiles()).containsEntry("src/A.java", "class A {}");
    }

    @Test
    void nullUncommittedFilesNormalizedToEmptyMap() {
        var dto = new IrisStruggleInterventionRequestDTO(null, null, null, null, null, null);
        assertThat(dto.uncommittedFiles()).isNotNull().isEmpty();
    }

    @Test
    void defaultIntentIsDecide_whenNotPresent() throws Exception {
        String json = """
                {"struggleSignal":{"alert":{"tSessionS":540,"primaryBoundary":"FM","boundaryTypes":["FM"],"severity":0.7,"path":"armed","inWarmup":false,"inGrace":false},
                   "trajectory":[],"dominantComponents":[],"sessionSeconds":540},
                 "uncommittedFiles":{}}""";
        var dto = mapper.readValue(json, IrisStruggleInterventionRequestDTO.class);
        assertThat(dto.intent()).isEqualTo("decide");
    }

    @Test
    void deserializesIntent_episode_confirmReason_requestToken() throws Exception {
        String json = """
                {"struggleSignal":{"alert":{"tSessionS":540,"primaryBoundary":"FM","boundaryTypes":["FM"],"severity":0.7,"path":"armed","inWarmup":false,"inGrace":false},
                   "trajectory":[],"dominantComponents":[],"sessionSeconds":540},
                 "uncommittedFiles":{},
                 "intent":"stale_check","confirmReason":"stale_solved","requestToken":"rt-1",
                 "episode":{"episodeId":"ep-1","isNew":false,"hints":[{"level":"ambient","text":"x","atSessionS":42.0}]}}""";
        var dto = mapper.readValue(json, IrisStruggleInterventionRequestDTO.class);
        assertThat(dto.intent()).isEqualTo("stale_check");
        assertThat(dto.confirmReason()).isEqualTo("stale_solved");
        assertThat(dto.requestToken()).isEqualTo("rt-1");
        assertThat(dto.episode()).isNotNull();
        assertThat(dto.episode().episodeId()).isEqualTo("ep-1");
        assertThat(dto.episode().isNew()).isFalse();
        assertThat(dto.episode().hints()).hasSize(1);
        assertThat(dto.episode().hints().get(0).level()).isEqualTo("ambient");
        assertThat(dto.episode().hints().get(0).text()).isEqualTo("x");
        assertThat(dto.episode().hints().get(0).atSessionS()).isEqualTo(42.0);
    }

    @Test
    void confirmReasonSnakeValuesRoundTrip() throws Exception {
        for (String reason : List.of("progress", "stale_solved", "parked_progress")) {
            String json = """
                    {"struggleSignal":{"alert":{"tSessionS":1,"primaryBoundary":"FM","boundaryTypes":["FM"],"severity":0.1,"path":"armed","inWarmup":false,"inGrace":false},
                       "trajectory":[],"dominantComponents":[],"sessionSeconds":1},
                     "uncommittedFiles":{},"confirmReason":"%s"}""".formatted(reason);
            var dto = mapper.readValue(json, IrisStruggleInterventionRequestDTO.class);
            assertThat(dto.confirmReason()).as("confirmReason '%s' must round-trip exactly", reason).isEqualTo(reason);
        }
    }
}
