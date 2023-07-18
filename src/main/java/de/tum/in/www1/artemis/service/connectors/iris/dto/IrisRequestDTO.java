package de.tum.in.www1.artemis.service.connectors.iris.dto;

import java.util.Map;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;

public record IrisRequestDTO(IrisTemplate template, String preferredModel, Map<String, Object> parameters) {
}
