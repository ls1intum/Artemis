package de.tum.in.www1.artemis.service.connectors.iris.dto;

import java.util.Map;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.service.connectors.iris.IrisModel;

public record IrisRequestDTO(IrisTemplate template, IrisModel preferredModel, Map<String, Object> parameters) {
}
