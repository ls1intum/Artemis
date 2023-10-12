package de.tum.in.www1.artemis.service.connectors.iris.dto;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;

public record IrisMessageResponseDTO(String usedModel, IrisMessage message) {
}
