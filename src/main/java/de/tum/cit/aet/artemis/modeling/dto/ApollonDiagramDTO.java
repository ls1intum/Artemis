package de.tum.cit.aet.artemis.modeling.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.modeling.domain.ApollonDiagram;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;

/**
 * DTO representing an {@link ApollonDiagram} on the REST response boundary.
 * <p>
 * Mirrors the previous entity wire shape (id, title, jsonRepresentation, diagramType, courseId). {@code NON_EMPTY}
 * inclusion matches the {@link ApollonDiagram} entity (and its {@code DomainObject} superclass) so the serialized
 * payload is unchanged after the migration.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApollonDiagramDTO(Long id, String title, String jsonRepresentation, DiagramType diagramType, Long courseId) implements Serializable {

    /**
     * Converts an {@link ApollonDiagram} into an {@link ApollonDiagramDTO}.
     *
     * @param diagram the diagram to convert
     * @return the converted DTO, or {@code null} if the diagram is {@code null}
     */
    public static ApollonDiagramDTO of(ApollonDiagram diagram) {
        if (diagram == null) {
            return null;
        }
        return new ApollonDiagramDTO(diagram.getId(), diagram.getTitle(), diagram.getJsonRepresentation(), diagram.getDiagramType(), diagram.getCourseId());
    }
}
