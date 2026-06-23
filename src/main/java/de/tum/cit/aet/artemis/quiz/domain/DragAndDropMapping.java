package de.tum.cit.aet.artemis.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A DragAndDropMapping.
 */
// No @Cache here on purpose: part of the quiz-submission merge graph. See #12574 / #12584.
@Entity
@Table(name = "drag_and_drop_mapping")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropMapping extends DomainObject implements QuizQuestionComponent<DragAndDropQuestion> {

    @Column(name = "drag_item_index")
    private Integer dragItemIndex;

    @Column(name = "drop_location_index")
    private Integer dropLocationIndex;

    @Column(name = "invalid")
    private Boolean invalid = false;

    @ManyToOne
    private DragItem dragItem;

    @ManyToOne
    private DropLocation dropLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private DragAndDropSubmittedAnswer submittedAnswer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
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

    /**
     * Stable, constant hashCode that does not change when Hibernate assigns the id on persist. This is required because
     * {@link DragAndDropQuestion#correctMappings} is a {@code Set<DragAndDropMapping>}: factories and DTO mappers add
     * transient mappings (id == null) to the set, and the id-based default would change after persist and silently
     * break HashSet membership. Returning a constant forces all instances into the same bucket; the (id-based)
     * {@code equals} contract still distinguishes them. The performance impact is negligible — a question's mapping
     * set is small. Mirrors the same pattern on {@link de.tum.cit.aet.artemis.assessment.domain.Feedback}.
     */
    @Override
    public int hashCode() {
        return DragAndDropMapping.class.hashCode();
    }
}
