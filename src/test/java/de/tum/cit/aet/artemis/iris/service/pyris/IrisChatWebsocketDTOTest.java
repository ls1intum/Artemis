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

    private static IrisMessageResponseDTO message() {
        return new IrisMessageResponseDTO(1L, ZonedDateTime.parse("2026-07-05T12:00:00Z"), null, IrisMessageSender.LLM, null, null, null, List.of(), null, null, null, null, null);
    }

    private static PyrisActivityDTO activity() {
        return new PyrisActivityDTO("activity-1", PyrisActivityKind.TOOL, "lecture_content_retrieval", PyrisActivityState.FINISHED, "Lecture 1", "2 chunks", 120L);
    }
}
