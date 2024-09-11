package de.tum.cit.aet.artemis.domain.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.view.QuizView;

/**
 * A DragAndDropMapping.
 */
@Entity
@Table(name = "drag_and_drop_mapping")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropMapping extends DomainObject implements QuizQuestionComponent<DragAndDropQuestion> {

    @Column(name = "drag_item_index")
    @JsonView(QuizView.Before.class)
    private Integer dragItemIndex;

    @Column(name = "drop_location_index")
    @JsonView(QuizView.Before.class)
    private Integer dropLocationIndex;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid = false;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private DragItem dragItem;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private DropLocation dropLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private DragAndDropSubmittedAnswer submittedAnswer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private DragAndDropQuestion question;

    public Integer getDragItemIndex() {
        return dragItemIndex;
    }

    public void setDragItemIndex(Integer dragItemIndex) {
        this.dragItemIndex = dragItemIndex;
    }

    public Integer getDropLocationIndex() {
        return dropLocationIndex;
    }

    public void setDropLocationIndex(Integer dropLocationIndex) {
        this.dropLocationIndex = dropLocationIndex;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public DragItem getDragItem() {
        return dragItem;
    }

    public DragAndDropMapping dragItem(DragItem dragItem) {
        this.dragItem = dragItem;
        return this;
    }

    public void setDragItem(DragItem dragItem) {
        this.dragItem = dragItem;
    }

    public DropLocation getDropLocation() {
        return dropLocation;
    }

    public DragAndDropMapping dropLocation(DropLocation dropLocation) {
        this.dropLocation = dropLocation;
        return this;
    }

    public void setDropLocation(DropLocation dropLocation) {
        this.dropLocation = dropLocation;
    }

    public DragAndDropSubmittedAnswer getSubmittedAnswer() {
        return submittedAnswer;
    }

    public void setSubmittedAnswer(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) {
        this.submittedAnswer = dragAndDropSubmittedAnswer;
    }

    public DragAndDropQuestion getQuestion() {
        return question;
    }

    @Override
    public void setQuestion(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
    }

    @Override
    public String toString() {
        return "DragAndDropMapping{" + "id=" + getId() + ", dragItemIndex='" + getDragItemIndex() + "'" + ", dropLocationIndex='" + getDropLocationIndex() + "'" + ", invalid='"
                + isInvalid() + "'" + "}";
    }
}
