package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;
import java.util.List;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;

public record IrisSessionDTO(Long id, Long userId, List<IrisMessage> irisMessages, ZonedDateTime creationDate, String chatMode, Long entityId) {
}
