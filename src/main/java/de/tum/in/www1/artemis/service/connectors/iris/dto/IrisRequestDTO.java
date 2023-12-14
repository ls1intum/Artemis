package de.tum.in.www1.artemis.service.connectors.iris.dto;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;

public record IrisRequestDTO(IrisTemplate template, String preferredModel, Object parameters) {
}
