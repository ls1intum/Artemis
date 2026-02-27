package de.tum.cit.aet.artemis.atlas.dto.atlasAgent;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.dto.CompetencyGraphEdgeDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyGraphNodeDTO;

/**
 * DTO for relation graph preview containing nodes and edges.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RelationGraphPreviewDTO(List<CompetencyGraphNodeDTO> nodes, List<CompetencyGraphEdgeDTO> edges, @Nullable Boolean viewOnly) {
}
