package de.tum.in.www1.artemis.service.connectors.iris.dto;

import java.util.Map;

public record IrisRequestV2DTO(String template, String preferredModel, Map<String, Object> parameters) {
}
