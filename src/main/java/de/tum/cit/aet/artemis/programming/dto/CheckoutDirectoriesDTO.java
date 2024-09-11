package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanCheckoutDirectoriesDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CheckoutDirectoriesDTO(BuildPlanCheckoutDirectoriesDTO submissionBuildPlanCheckoutDirectories, BuildPlanCheckoutDirectoriesDTO solutionBuildPlanCheckoutDirectories) {
}
