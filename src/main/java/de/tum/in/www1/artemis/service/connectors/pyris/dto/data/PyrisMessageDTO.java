package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMessageDTO(Instant sentAt, IrisMessageSender sender, List<PyrisMessageContent> contents) {
}
