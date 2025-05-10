package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for using a post in CreatAnswerPostDTO with only the id.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParentPostDTO(long id) {

}
