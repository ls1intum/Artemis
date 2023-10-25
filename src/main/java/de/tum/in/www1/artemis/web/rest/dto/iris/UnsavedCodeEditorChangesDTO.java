package de.tum.in.www1.artemis.web.rest.dto.iris;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnsavedCodeEditorChangesDTO(Optional<String> problemStatement, Optional<Map<String, String>> templateRepository, Optional<Map<String, String>> solutionRepository,
        Optional<Map<String, String>> testRepository) {
}
