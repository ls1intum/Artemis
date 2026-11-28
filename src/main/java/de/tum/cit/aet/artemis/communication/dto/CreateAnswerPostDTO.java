package de.tum.cit.aet.artemis.communication.dto;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for creating an Answer Post with only the necessary fields.
 * <p>
 * The content is capped at 5000 characters. Keep this limit in sync with the client editor (MAX_CONTENT_LENGTH in posting-create-edit.directive.ts) and the limit declared on the
 * Posting entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateAnswerPostDTO(@Size(max = 5000) String content, ParentPostDTO post) {

}
