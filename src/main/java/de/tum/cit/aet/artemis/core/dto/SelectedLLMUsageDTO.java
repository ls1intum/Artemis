package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SelectedLLMUsageDTO(AiSelectionDecision selection) {
}
