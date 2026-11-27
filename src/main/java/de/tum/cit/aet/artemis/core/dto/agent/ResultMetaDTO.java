package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code _meta} of a result of the Model Context Protocol.
 *
 * @param serverInfo which server answers, for display and logging only
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultMetaDTO(@JsonProperty("io.modelcontextprotocol/serverInfo") ServerInfoDTO serverInfo) {
}
