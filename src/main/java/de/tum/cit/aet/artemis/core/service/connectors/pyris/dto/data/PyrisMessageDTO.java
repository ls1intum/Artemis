package de.tum.cit.aet.artemis.core.service.connectors.pyris.dto.data;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMessageDTO(Instant sentAt, IrisMessageSender sender, List<PyrisMessageContentBaseDTO> contents) {

    /**
     * Convert an IrisMessage to a PyrisMessageDTO.
     *
     * @param message The message to convert.
     * @return The converted message.
     */
    public static PyrisMessageDTO of(IrisMessage message) {
        var content = message.getContent().stream().map(messageContent -> {
            PyrisMessageContentBaseDTO result = null;
            if (messageContent.getClass().equals(IrisTextMessageContent.class)) {
                result = new PyrisTextMessageContentDTO(messageContent.getContentAsString());
            }
            else if (messageContent.getClass().equals(IrisJsonMessageContent.class)) {
                result = new PyrisJsonMessageContentDTO(messageContent.getContentAsString());
            }
            return result;
        }).filter(Objects::nonNull).toList();
        return new PyrisMessageDTO(toInstant(message.getSentAt()), message.getSender(), content);
    }
}
