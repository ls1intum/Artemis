package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;

public record IrisMessageDTO(IrisMessage message, IrisClientArgumentsDTO context) {
}
