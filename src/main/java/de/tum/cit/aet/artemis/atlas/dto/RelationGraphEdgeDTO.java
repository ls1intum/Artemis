package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing an edge in the relation graph preview.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RelationGraphEdgeDTO(String id, String source, String target, String label) {
}
