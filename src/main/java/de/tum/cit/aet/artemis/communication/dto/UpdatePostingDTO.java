package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for updating a Posting with only the necessary fields.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdatePostingDTO(long id, String content, String title, boolean resolvesPost) {

}
