package de.tum.cit.aet.artemis.quiz.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DropLocation.
 */
@Entity
@Table(name = "drop_location")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DropLocation extends TempIdObject implements QuizQuestionComponent<DragAndDropQuestion> {

    @Column(name = "pos_x")
    private Double posX;

    @Column(name = "pos_y")
    private Double posY;

    @Column(name = "width")
    private Double width;

    @Column(name = "height")
    private Double height;

    @Column(name = "invalid")
    private Boolean invalid = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private DragAndDropQuestion question;

    // NOTE: without cascade and orphanRemoval, deletion of quizzes might not work properly, so we reference mappings here, even if we do not use them
    @OneToMany(cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "dropLocation")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DragAndDropMapping> mappings = new HashSet<>();

    public Double getPosX() {
        return posX;
    }

    public DropLocation posX(Double posX) {
        this.posX = posX;
        return this;
    }

    public void setPosX(Double posX) {
        this.posX = posX;
    }

    public Double getPosY() {
        return posY;
    }

    public DropLocation posY(Double posY) {
        this.posY = posY;
        return this;
    }

    public void setPosY(Double posY) {
        this.posY = posY;
    }

    public Double getWidth() {
        return width;
    }

    public DropLocation width(Double width) {
        this.width = width;
        return this;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public Double getHeight() {
        return height;
    }

    public DropLocation height(Double height) {
        this.height = height;
        return this;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public DragAndDropQuestion getQuestion() {
        return question;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    @Override
    public void setQuestion(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public void setMappings(Set<DragAndDropMapping> mappings) {
        this.mappings = mappings;
    }

    /**
     * check if the DropLocation is solved correctly
     *
     * @param dndAnswer Answer from the student with the List of submittedMappings from the Result
     * @return if the drop location is correct
     */
    public boolean isDropLocationCorrect(DragAndDropSubmittedAnswer dndAnswer) {

        Set<DragItem> correctDragItems = question.getCorrectDragItemsForDropLocation(this);
        DragItem selectedDragItem = dndAnswer.getSelectedDragItemForDropLocation(this);

        return ((correctDragItems.isEmpty() && selectedDragItem == null) || (selectedDragItem != null && correctDragItems.contains(selectedDragItem)));
        // this drop location was meant to stay empty and user didn't drag anything onto it
        // OR the user dragged one of the correct drag items onto this drop location
        // => this is correct => Return true;
    }

    @Override
    public String toString() {
        return "DropLocation{" + "id=" + getId() + ", posX='" + getPosX() + "'" + ", posY='" + getPosY() + "'" + ", width='" + getWidth() + "'" + ", height='" + getHeight() + "'"
                + ", invalid='" + isInvalid() + "'" + "}";
    }

}
