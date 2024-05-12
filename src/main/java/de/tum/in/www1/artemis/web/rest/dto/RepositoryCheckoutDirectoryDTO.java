package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryCheckoutDirectoryDTO(Map<ProgrammingLanguage, CheckoutDirectoryInfo> checkoutDirectories) {

}
