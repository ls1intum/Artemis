package de.tum.cit.aet.artemis.atlas.dto.atlasAgent;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.dto.RelationGraphEdgeDTO;
import de.tum.cit.aet.artemis.atlas.dto.RelationGraphNodeDTO;

/**
 * DTO for relation graph preview containing nodes and edges.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RelationGraphPreviewDTO(List<RelationGraphNodeDTO> nodes, List<RelationGraphEdgeDTO> edges, @Nullable Boolean viewOnly) {
}
