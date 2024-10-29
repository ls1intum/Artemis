package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildPlanCheckoutDirectoriesDTO(String exerciseCheckoutDirectory, String solutionCheckoutDirectory, String testCheckoutDirectory) {

}
