package de.tum.in.www1.artemis.service.connectors.iris.dto;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.service.connectors.iris.IrisModel;

public record IrisMessageResponseDTO(IrisModel usedModel, IrisMessage message) {
}
