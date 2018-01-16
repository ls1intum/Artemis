package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DragAndDropAssignment.
 */
@Entity
@Table(name = "drag_and_drop_assignment")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DragAndDropAssignment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private DragItem item;

    @ManyToOne
    private DropLocation location;

    @ManyToOne
    @JsonIgnore
    private DragAndDropSubmittedAnswer submittedAnswer;

    @ManyToOne
    @JsonIgnore
    private DragAndDropQuestion question;

    private Integer itemIndex;

    private Integer locationIndex;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DragItem getItem() {
        return item;
    }

    public DragAndDropAssignment item(DragItem dragItem) {
        this.item = dragItem;
        return this;
    }

    public void setItem(DragItem dragItem) {
        this.item = dragItem;
    }

    public DropLocation getLocation() {
        return location;
    }

    public DragAndDropAssignment location(DropLocation dropLocation) {
        this.location = dropLocation;
        return this;
    }

    public void setLocation(DropLocation dropLocation) {
        this.location = dropLocation;
    }

    public DragAndDropSubmittedAnswer getSubmittedAnswer() {
        return submittedAnswer;
    }

    public DragAndDropAssignment submittedAnswer(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) {
        this.submittedAnswer = dragAndDropSubmittedAnswer;
        return this;
    }

    public void setSubmittedAnswer(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) {
        this.submittedAnswer = dragAndDropSubmittedAnswer;
    }

    public DragAndDropQuestion getQuestion() {
        return question;
    }

    public DragAndDropAssignment question(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
        return this;
    }

    public void setQuestion(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
    }

    public Integer getItemIndex() {
        return itemIndex;
    }

    public DragAndDropAssignment itemIndex(Integer itemIndex) {
        this.itemIndex = itemIndex;
        return this;
    }

    public void setItemIndex(Integer itemIndex) {
        this.itemIndex = itemIndex;
    }

    public Integer getLocationIndex() {
        return locationIndex;
    }

    public DragAndDropAssignment locationIndex(Integer locationIndex) {
        this.locationIndex = locationIndex;
        return this;
    }

    public void setLocationIndex(Integer locationIndex) {
        this.locationIndex = locationIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DragAndDropAssignment dragAndDropAssignment = (DragAndDropAssignment) o;
        if (dragAndDropAssignment.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dragAndDropAssignment.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DragAndDropAssignment{" +
            "id=" + getId() +
            "}";
    }
}
