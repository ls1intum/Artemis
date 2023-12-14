package de.tum.in.www1.artemis.service.dto.iris.codeeditor;

import java.util.Map;

public record IrisCodeEditorGenerationDTO(String problemStatement, Map<String, String> solutionRepository, Map<String, String> templateRepository,
        Map<String, String> testRepository, String instructions) {
}
