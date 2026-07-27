package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMessageDTO(@Nullable Long id, Instant sentAt, IrisMessageSender sender, List<PyrisMessageContentBaseDTO> contents) {

    /**
     * Convert an IrisMessage to a PyrisMessageDTO.
     * <p>
     * Text content is forwarded as text, JSON content unchanged as JSON. That includes marker messages such as a COMMAND marker recording a past point-out: like a CTXSWAP marker,
     * it travels as the JSON it is stored as, and Pyris turns it into the system note the LLM reads. Artemis does not phrase anything for the LLM here.
     *
     * @param message The message to convert.
     * @return The converted message.
     */
    public static PyrisMessageDTO of(IrisMessage message) {
        var content = message.getContent().stream().map(messageContent -> {
            if (messageContent instanceof IrisTextMessageContent) {
                return (PyrisMessageContentBaseDTO) new PyrisTextMessageContentDTO(messageContent.getContentAsString());
            }
            if (messageContent instanceof IrisJsonMessageContent) {
                return (PyrisMessageContentBaseDTO) new PyrisJsonMessageContentDTO(messageContent.getContentAsString());
            }
            return null;
        }).filter(Objects::nonNull).toList();
        return new PyrisMessageDTO(message.getId(), toInstant(message.getSentAt()), message.getSender(), content);
    }
}
