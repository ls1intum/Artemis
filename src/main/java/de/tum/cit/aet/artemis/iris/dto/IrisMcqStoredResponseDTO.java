package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An answer already stored in MCQ content. Extension fields belong to the stored content, not to the student's update request.
 *
 * @param selectedIndex        the index of the selected option
 * @param submitted            whether the answer has been submitted
 * @param questionIndex        the question within an MCQ set, or null for a standalone question
 * @param additionalProperties extension fields retained when another answer or this answer is updated
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMcqStoredResponseDTO(int selectedIndex, boolean submitted, @Nullable Integer questionIndex,
        @Nullable @JsonAnySetter @JsonAnyGetter Map<String, Object> additionalProperties) {
}
