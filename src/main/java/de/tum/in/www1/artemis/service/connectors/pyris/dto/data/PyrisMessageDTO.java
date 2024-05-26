package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import static de.tum.in.www1.artemis.service.util.ZonedDateTimeUtil.toInstant;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.message.IrisJsonMessageContent;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMessageDTO(Instant sentAt, IrisMessageSender sender, List<PyrisMessageContentDTO> contents) {

    public static PyrisMessageDTO of(IrisMessage message) {
        var content = message.getContent().stream().map(messageContent -> {
            PyrisMessageContentDTO result = null;
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
