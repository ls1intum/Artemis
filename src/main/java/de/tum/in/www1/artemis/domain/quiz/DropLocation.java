package de.tum.in.www1.artemis.domain.quiz;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.TempIdObject;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A DropLocation.
 */
@Entity
@Table(name = "drop_location")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DropLocation extends TempIdObject {

    @Column(name = "pos_x")
    @JsonView(QuizView.Before.class)
    private Double posX;

    @Column(name = "pos_y")
    @JsonView(QuizView.Before.class)
    private Double posY;

    @Column(name = "width")
    @JsonView(QuizView.Before.class)
    private Double width;

    @Column(name = "height")
    @JsonView(QuizView.Before.class)
    private Double height;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid = false;

    @ManyToOne
    @JsonIgnore
    private DragAndDropQuestion question;

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

    public void setQuestion(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
    }

    public Set<DragAndDropMapping> getMappings() {
        return mappings;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
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
