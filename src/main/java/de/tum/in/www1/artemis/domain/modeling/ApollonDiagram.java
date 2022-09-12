package de.tum.in.www1.artemis.domain.modeling;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;

/**
 * An ApollonDiagram.
 */
@Entity
@Table(name = "apollon_diagram")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApollonDiagram extends DomainObject {

    @Column(name = "title")
    private String title;

    @Column(name = "json_representation")
    // @Lob
    private String jsonRepresentation;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagram_type")
    private DiagramType diagramType;

    @Column(name = "course_id")
    private Long courseId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getJsonRepresentation() {
        return jsonRepresentation;
    }

    public void setJsonRepresentation(String jsonRepresentation) {
        this.jsonRepresentation = jsonRepresentation;
    }

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(DiagramType diagramType) {
        this.diagramType = diagramType;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    @Override
    public String toString() {
        return "ApollonDiagram{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", jsonRepresentation='" + getJsonRepresentation() + "'" + "}";
    }
}
