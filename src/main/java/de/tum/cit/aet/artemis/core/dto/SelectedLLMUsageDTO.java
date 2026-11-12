package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SelectedLLMUsageDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) AiSelectionDecision selection) {
}
