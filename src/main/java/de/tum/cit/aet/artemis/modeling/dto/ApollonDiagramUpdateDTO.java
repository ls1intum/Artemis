package de.tum.cit.aet.artemis.modeling.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.modeling.domain.ApollonDiagram;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;

/**
 * DTO for creating and updating ApollonDiagrams.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApollonDiagramUpdateDTO(@Nullable Long id, @Nullable String title, @Nullable String jsonRepresentation, @Nullable DiagramType diagramType, @NotNull Long courseId) {

    /**
     * Creates an ApollonDiagramUpdateDTO from the given ApollonDiagram domain object.
     *
     * @param diagram the ApollonDiagram to convert
     * @return the corresponding DTO
     */
    public static ApollonDiagramUpdateDTO of(ApollonDiagram diagram) {
        return new ApollonDiagramUpdateDTO(diagram.getId(), diagram.getTitle(), diagram.getJsonRepresentation(), diagram.getDiagramType(), diagram.getCourseId());
    }

    /**
     * Creates a new ApollonDiagram entity from this DTO.
     * Used for create operations.
     *
     * @return a new ApollonDiagram entity
     */
    public ApollonDiagram toEntity() {
        ApollonDiagram diagram = new ApollonDiagram();
        diagram.setTitle(title);
        diagram.setJsonRepresentation(jsonRepresentation);
        diagram.setDiagramType(diagramType);
        diagram.setCourseId(courseId);
        return diagram;
    }

    /**
     * Applies the DTO values to an existing ApollonDiagram entity.
     * This updates the managed entity with values from the DTO.
     *
     * @param diagram the existing diagram to update
     */
    public void applyTo(ApollonDiagram diagram) {
        diagram.setTitle(title);
        diagram.setJsonRepresentation(jsonRepresentation);
        diagram.setDiagramType(diagramType);
        diagram.setCourseId(courseId);
    }
}
