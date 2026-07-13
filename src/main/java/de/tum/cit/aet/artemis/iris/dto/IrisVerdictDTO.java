package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisVerdictDTO(IrisVerdict verdict, String reasoning) {
}
