package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisJsonMessageContentDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;

/**
 * Unit tests for how marker messages reach Pyris. A COMMAND marker travels as the JSON it is stored as — like a CTXSWAP
 * marker — and Pyris turns it into the note the LLM reads; Artemis phrases nothing for the LLM here.
 */
class PyrisMessageDTOTest {

    private static final String POINT_OUT_MARKER = """
            {"type":"pointOut","parameters":{"lectureUnitId":42,"lectureUnitName":"Intro","page":3}}
            """;

    private static IrisMessage commandMarker(String markerJson) {
        var message = new IrisMessage();
        var content = new IrisJsonMessageContent();
        content.setJsonContent(markerJson);
        message.addContent(content);
        message.setSender(IrisMessageSender.COMMAND);
        return message;
    }

    private static IrisMessage userMessage(String text) {
        var message = new IrisMessage();
        message.addContent(new IrisTextMessageContent(text));
        message.setSender(IrisMessageSender.USER);
        return message;
    }

    @Test
    void of_forwardsCommandMarkerAsJson() {
        var dto = PyrisMessageDTO.of(commandMarker(POINT_OUT_MARKER));

        assertThat(dto.sender()).isEqualTo(IrisMessageSender.COMMAND);
        assertThat(dto.contents()).hasSize(1);
        // Unchanged JSON: the wording the LLM reads is built in Pyris, next to the prompts.
        assertThat(((PyrisJsonMessageContentDTO) dto.contents().getFirst()).jsonContent()).contains("\"type\":\"pointOut\"", "\"lectureUnitId\":42", "\"page\":3");
    }

    @Test
    void toPyrisMessageDTOList_leavesOutMessagesWithoutContent() {
        // Safety net: a message whose content could not be converted carries nothing for Pyris, whose message
        // converter rejects an empty content list outright.
        var service = new PyrisDTOService(null);
        var messages = List.of(userMessage("What is quicksort?"), new IrisMessage());

        var dtos = service.toPyrisMessageDTOList(messages);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.getFirst().sender()).isEqualTo(IrisMessageSender.USER);
    }
}
