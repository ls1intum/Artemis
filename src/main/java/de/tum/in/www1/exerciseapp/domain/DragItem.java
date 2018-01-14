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
 * A DragItem.
 */
@Entity
@Table(name = "drag_item")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DragItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "picture_file_path")
    private String pictureFilePath;

    @Column(name = "text")
    private String text;

    @ManyToOne
    @JsonIgnore
    private DragAndDropQuestion question;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JsonIgnore
    @JoinColumn(name = "item_id")
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

    public String getPictureFilePath() {
        return pictureFilePath;
    }

    public DragItem pictureFilePath(String pictureFilePath) {
        this.pictureFilePath = pictureFilePath;
        return this;
    }

    public void setPictureFilePath(String pictureFilePath) {
        this.pictureFilePath = pictureFilePath;
    }

    public String getText() {
        return text;
    }

    public DragItem text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public DragAndDropQuestion getQuestion() {
        return question;
    }

    public DragItem question(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
        return this;
    }

    public void setQuestion(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
    }

    public Set<DragAndDropAssignment> getAssignments() {
        return assignments;
    }

    public DragItem assignments(Set<DragAndDropAssignment> assignments) {
        this.assignments = assignments;
        return this;
    }

    public DragItem addAssignments(DragAndDropAssignment assignment) {
        this.assignments.add(assignment);
        assignment.setItem(this);
        return this;
    }

    public DragItem removeAssignments(DragAndDropAssignment assignment) {
        this.assignments.remove(assignment);
        assignment.setItem(null);
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
        DragItem dragItem = (DragItem) o;
        if (dragItem.getTempID() != null && getTempID() != null && Objects.equals(getTempID(), dragItem.getTempID())) {
            return true;
        }
        if (dragItem.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dragItem.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DragItem{" +
            "id=" + getId() +
            ", pictureFilePath='" + getPictureFilePath() + "'" +
            ", text='" + getText() + "'" +
            "}";
    }
}
