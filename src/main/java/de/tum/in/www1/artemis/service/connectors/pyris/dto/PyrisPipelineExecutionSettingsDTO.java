package de.tum.in.www1.artemis.service.connectors.pyris.dto;

import java.util.List;

public record PyrisPipelineExecutionSettingsDTO(String authenticationToken, List<String> allowedModels, String gitUsername, String gitPassword) {
}
