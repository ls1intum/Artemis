package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildPlanCheckoutDirectoriesDTO(String exerciseCheckoutDirectory, String solutionCheckoutDirectory, String testCheckoutDirectory) {

}
