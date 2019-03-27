package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A ApollonDiagram.
 */
@Entity
@Table(name = "apollon_diagram")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApollonDiagram implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "json_representation")
    @Lob
    private String jsonRepresentation;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagram_type")
    private DiagramType diagramType;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public ApollonDiagram title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getJsonRepresentation() {
        return jsonRepresentation;
    }

    public ApollonDiagram jsonRepresentation(String jsonRepresentation) {
        this.jsonRepresentation = jsonRepresentation;
        return this;
    }

    public void setJsonRepresentation(String jsonRepresentation) {
        this.jsonRepresentation = jsonRepresentation;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public ApollonDiagram diagramType(DiagramType diagramType) {
        this.diagramType = diagramType;
        return this;
    }

    public void setDiagramType(DiagramType diagramType) {
        this.diagramType = diagramType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ApollonDiagram apollonDiagram = (ApollonDiagram) o;
        if (apollonDiagram.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), apollonDiagram.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ApollonDiagram{" +
            "id=" + getId() +
            ", title='" + getTitle() + "'" +
            ", jsonRepresentation='" + getJsonRepresentation() + "'" +
            "}";
    }
}
