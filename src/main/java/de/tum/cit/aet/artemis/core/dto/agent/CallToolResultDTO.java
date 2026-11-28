package de.tum.cit.aet.artemis.core.dto.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The result of {@code tools/call}.
 *
 * @param resultType always {@code complete}: the call is finished
 * @param content    what the tool reports, as text
 * @param isError    whether the tool failed
 * @param meta       which server answers
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CallToolResultDTO(String resultType, List<ToolContentDTO> content, boolean isError, @JsonProperty("_meta") ResultMetaDTO meta) {
}
