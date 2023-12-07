package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Map;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;

public record IrisMessageDTO(IrisMessage message, Map<String, Object> options) {
}
