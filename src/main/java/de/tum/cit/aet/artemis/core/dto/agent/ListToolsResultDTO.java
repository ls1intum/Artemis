package de.tum.cit.aet.artemis.core.dto.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The result of {@code tools/list}.
 *
 * @param resultType always {@code complete}
 * @param tools      the tools the token may use
 * @param ttlMs      how long a client may reuse the list, in milliseconds
 * @param cacheScope {@code private}, because the tools depend on the scopes of the token
 * @param meta       which server answers
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ListToolsResultDTO(String resultType, List<ToolDescriptionDTO> tools, long ttlMs, String cacheScope, @JsonProperty("_meta") ResultMetaDTO meta) {
}
