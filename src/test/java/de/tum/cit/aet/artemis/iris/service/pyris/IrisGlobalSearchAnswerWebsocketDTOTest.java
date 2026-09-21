package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.dto.IrisGlobalSearchAnswerWebsocketDTO;

class IrisGlobalSearchAnswerWebsocketDTOTest {

    @Test
    void clearDraftSurvivesSerializationEvenThoughItsFalseByDefault() {
        // clearDraft=true must reach the client distinguishable from "no partial result in this message", which an
        // empty-string partialResult could not do on its own once NON_EMPTY strips it identically to null.
        var dto = new IrisGlobalSearchAnswerWebsocketDTO("run-1", true, null, null, null, 7, null, true, false, null, null);

        String json = JsonObjectMapper.get().writeValueAsString(dto);

        assertThat(json).as("clearDraft is a boolean, never stripped by NON_EMPTY regardless of its value").contains("\"clearDraft\":true");
        assertThat(json).as("partialResult stays omitted when there is no new streamed content").doesNotContain("\"partialResult\"");
    }

    @Test
    void failedSurvivesSerializationDistinguishingItFromASuccessfulNoAnswerResult() {
        // failed=true must reach the client distinguishable from a successful run with no relevant answer, which
        // otherwise produces the identical isThinking=false, answer=null shape.
        var dto = new IrisGlobalSearchAnswerWebsocketDTO("run-1", false, null, null, null, null, null, false, true, null, null);

        String json = JsonObjectMapper.get().writeValueAsString(dto);

        assertThat(json).as("failed is a boolean, never stripped by NON_EMPTY regardless of its value").contains("\"failed\":true");
        assertThat(json).as("answer stays omitted for a failed run").doesNotContain("\"answer\"");
    }

    @Test
    void stageSurvivesSerializationOnAThinkingUpdateWithNoPartialResultYet() {
        var dto = IrisGlobalSearchAnswerWebsocketDTO.thinking("run-1", "searching", null);

        String json = JsonObjectMapper.get().writeValueAsString(dto);

        assertThat(json).contains("\"stage\":\"searching\"");
        assertThat(json).as("answer stays omitted while only the stage is known").doesNotContain("\"answer\"");
    }

    @Test
    void stageSourcesSurviveSerializationAlongsideTheFoundStage() {
        var dto = IrisGlobalSearchAnswerWebsocketDTO.thinking("run-1", "found", List.of("Advanced Algorithms", "Software Engineering"));

        String json = JsonObjectMapper.get().writeValueAsString(dto);

        assertThat(json).contains("\"stageSources\":[\"Advanced Algorithms\",\"Software Engineering\"]");
    }

    @Test
    void missingStageIsOmittedFromSerializationForAnOlderPyrisThatNeverSendsIt() {
        var dto = new IrisGlobalSearchAnswerWebsocketDTO("run-1", true, null, null);

        String json = JsonObjectMapper.get().writeValueAsString(dto);

        assertThat(json).as("stage is additive; an update that never set it must not serialize a null/empty stage field").doesNotContain("\"stage\"");
        assertThat(json).as("stageSources is additive too").doesNotContain("\"stageSources\"");
    }
}
