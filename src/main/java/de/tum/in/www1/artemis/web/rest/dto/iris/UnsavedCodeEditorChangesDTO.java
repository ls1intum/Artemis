package de.tum.in.www1.artemis.web.rest.dto.iris;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnsavedCodeEditorChangesDTO(String problemStatement, Map<String, String> templateRepository, Map<String, String> solutionRepository,
        Map<String, String> testRepository) {
}
