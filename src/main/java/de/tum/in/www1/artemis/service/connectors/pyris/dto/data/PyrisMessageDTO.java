package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.LocalDateTime;
import java.util.List;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;

public record PyrisMessageDTO(LocalDateTime sentAt, IrisMessageSender sender, List<PyrisMessageContentDTO> contents) {
}
