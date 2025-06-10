package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;
import java.util.List;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;

public record IrisSessionDTO(Long id, Long userId, List<IrisMessage> irisMessages, ZonedDateTime creationDate, IrisSubSettingsType subSettingsType, Long entityId) {
}
