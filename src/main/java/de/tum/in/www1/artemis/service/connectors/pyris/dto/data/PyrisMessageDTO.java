package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.ZonedDateTime;
import java.util.List;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;

public record PyrisMessageDTO(ZonedDateTime sentAt, IrisMessageSender sender, List<PyrisMessageContentDTO> contents) {
}
