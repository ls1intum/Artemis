package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DropLocation.
 */
@Entity
@Table(name = "drop_location")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DropLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pos_x")
    private Integer posX;

    @Column(name = "pos_y")
    private Integer posY;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @ManyToOne
    @JsonIgnore
    private DragAndDropQuestion question;

    @OneToMany(cascade = CascadeType.REMOVE, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "location")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DragAndDropAssignment> assignments = new HashSet<>();

    @Transient
    // variable name must be different from Getter name,
    // so that Jackson ignores the @Transient annotation,
    // but Hibernate still respects it
    private Long tempIDTransient;

    public Long getTempID() {
        return tempIDTransient;
    }

    public void setTempID(Long tempID) {
        this.tempIDTransient = tempID;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPosX() {
        return posX;
    }

    public DropLocation posX(Integer posX) {
        this.posX = posX;
        return this;
    }

    public void setPosX(Integer posX) {
        this.posX = posX;
    }

    public Integer getPosY() {
        return posY;
    }

    public DropLocation posY(Integer posY) {
        this.posY = posY;
        return this;
    }

    public void setPosY(Integer posY) {
        this.posY = posY;
    }

    public Integer getWidth() {
        return width;
    }

    public DropLocation width(Integer width) {
        this.width = width;
        return this;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public DropLocation height(Integer height) {
        this.height = height;
        return this;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public DragAndDropQuestion getQuestion() {
        return question;
    }

    public DropLocation question(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
        return this;
    }

    public void setQuestion(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
    }

    public Set<DragAndDropAssignment> getAssignments() {
        return assignments;
    }

    public DropLocation assignments(Set<DragAndDropAssignment> assignments) {
        this.assignments = assignments;
        return this;
    }

    public DropLocation addAssignments(DragAndDropAssignment assignment) {
        this.assignments.add(assignment);
        assignment.setLocation(this);
        return this;
    }

    public DropLocation removeAssignments(DragAndDropAssignment assignment) {
        this.assignments.remove(assignment);
        assignment.setLocation(null);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DropLocation dropLocation = (DropLocation) o;
        if (dropLocation.getTempID() != null && getTempID() != null && Objects.equals(getTempID(), dropLocation.getTempID())) {
            return true;
        }
        if (dropLocation.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dropLocation.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DropLocation{" +
            "id=" + getId() +
            ", posX='" + getPosX() + "'" +
            ", posY='" + getPosY() + "'" +
            ", width='" + getWidth() + "'" +
            ", height='" + getHeight() + "'" +
            "}";
    }
}
