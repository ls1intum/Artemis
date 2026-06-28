package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

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
        var dto = new IrisStruggleInterventionRequestDTO(null, null);
        assertThat(dto.uncommittedFiles()).isNotNull().isEmpty();
    }
}
