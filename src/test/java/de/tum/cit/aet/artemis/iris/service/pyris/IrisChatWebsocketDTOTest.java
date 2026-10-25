package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.dto.IrisChatWebsocketDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisChatWebsocketDTO.IrisWebsocketMessageType;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityKind;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

class IrisChatWebsocketDTOTest {

    @Test
    void statusFrameCarriesRunScopedStateAndActivities() {
        var error = new PyrisStatusErrorDTO("failed", "tool_failed");
        var activity = activity();

        var dto = new IrisChatWebsocketDTO(null, null, PyrisRunState.FAILED, error, "New title", List.of("next question"), null, null, "run-1", null, null, List.of(activity), 4);

        assertThat(dto.type()).isEqualTo(IrisWebsocketMessageType.STATUS);
        assertThat(dto.runId()).isEqualTo("run-1");
        assertThat(dto.runState()).isEqualTo(PyrisRunState.FAILED);
        assertThat(dto.error()).isEqualTo(error);
        assertThat(dto.activities()).containsExactly(activity);
        assertThat(dto.activitySeq()).isEqualTo(4);
    }

    @Test
    void messageFrameCarriesRunScopedRunningState() {
        var message = message();

        var dto = new IrisChatWebsocketDTO(message, null, PyrisRunState.RUNNING, null, null, null, null, null, "run-2", null, null, null, null);

        assertThat(dto.type()).isEqualTo(IrisWebsocketMessageType.MESSAGE);
        assertThat(dto.message()).isEqualTo(message);
        assertThat(dto.runId()).isEqualTo("run-2");
        assertThat(dto.runState()).isEqualTo(PyrisRunState.RUNNING);
    }

    @Test
    void messageFrameCarriesIntermediateFinalFlag() {
        var message = message();

        var dto = new IrisChatWebsocketDTO(message, null, PyrisRunState.RUNNING, null, null, null, null, null, "run-2", null, null, null, null, false);

        assertThat(dto.type()).isEqualTo(IrisWebsocketMessageType.MESSAGE);
        assertThat(dto.finalResult()).isFalse();
    }

    @Test
    void messageResponseDtoMarksIntermediateMessagesAsFinalFalse() {
        var message = new IrisMessage();
        message.setId(7L);
        message.setSender(IrisMessageSender.LLM);
        message.addContent(new IrisTextMessageContent("Intermediate preamble"));
        message.setIntermediate(true);

        var dto = IrisMessageResponseDTO.of(message);

        assertThat(dto.finalResult()).isFalse();
    }

    @Test
    void partialFrameTakesPrecedenceOverMessageAndCarriesRunState() {
        var message = message();

        var dto = new IrisChatWebsocketDTO(message, null, PyrisRunState.RUNNING, null, null, null, null, null, "run-3", "draft answer", 6, null, null);

        assertThat(dto.type()).isEqualTo(IrisWebsocketMessageType.PARTIAL);
        assertThat(dto.message()).isEqualTo(message);
        assertThat(dto.partialResult()).isEqualTo("draft answer");
        assertThat(dto.partialSeq()).isEqualTo(6);
        assertThat(dto.runId()).isEqualTo("run-3");
        assertThat(dto.runState()).isEqualTo(PyrisRunState.RUNNING);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 })
    void websocketEqualityDetectsChangesInEveryWireField(int changedField) {
        var original = frameWithChange(-1);
        var identical = frameWithChange(-1);
        var changed = frameWithChange(changedField);
        assertThat(original).isEqualTo(identical).hasSameHashCodeAs(identical);
        assertThat(original).isNotEqualTo(changed);
        assertThat(changed).isNotEqualTo(original);
    }

    @Test
    void websocketEqualityHandlesIdentityNullAndOtherPayloadTypes() {
        var frame = frameWithChange(-1);
        assertThat(frame.equals(frame)).isTrue();
        assertThat(frame.equals(null)).isFalse();
        assertThat(frame.equals("frame")).isFalse();
    }

    private IrisChatWebsocketDTO frameWithChange(int field) {
        var tokens = List.of(new de.tum.cit.aet.artemis.admin.domain.LLMRequest("model", 10, 1f, 5, 1f, "pipeline"));
        var citations = List.of(new de.tum.cit.aet.artemis.iris.dto.IrisCitationMetaDTO(1L, "Lecture", "Unit", 2L, 3L));
        return new IrisChatWebsocketDTO(field == 0 ? IrisWebsocketMessageType.MESSAGE : IrisWebsocketMessageType.STATUS, field == 1 ? null : message(),
                field == 2 ? null : new de.tum.cit.aet.artemis.iris.service.IrisRateLimitService.IrisRateLimitInformation(1, 10, 1),
                field == 3 ? PyrisRunState.FAILED : PyrisRunState.RUNNING, field == 4 ? null : new PyrisStatusErrorDTO("message", "code"), field == 5 ? "Changed" : "Title",
                field == 6 ? List.of("other") : List.of("Suggestion"), field == 7 ? List.of() : tokens, field == 8 ? List.of() : citations, field == 9 ? "other-run" : "run",
                field == 10 ? "other draft" : "draft", field == 11 ? 2 : 1, field == 12 ? List.of() : List.of(activity()), field == 13 ? 2 : 1, field == 14 ? true : false);
    }

    private static IrisMessageResponseDTO message() {
        return new IrisMessageResponseDTO(1L, ZonedDateTime.parse("2026-07-05T12:00:00Z"), null, IrisMessageSender.LLM, null, null, null, List.of(), null, null, null);
    }

    private static PyrisActivityDTO activity() {
        return new PyrisActivityDTO("activity-1", PyrisActivityKind.TOOL, "lecture_content_retrieval", PyrisActivityState.FINISHED, "Lecture 1", "2 chunks", 120L);
    }
}
