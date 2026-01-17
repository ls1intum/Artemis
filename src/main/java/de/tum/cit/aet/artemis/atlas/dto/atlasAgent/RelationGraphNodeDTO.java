package de.tum.cit.aet.artemis.atlas.dto.atlasAgent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a node in the relation graph preview.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RelationGraphNodeDTO(String id, String label) {
}
