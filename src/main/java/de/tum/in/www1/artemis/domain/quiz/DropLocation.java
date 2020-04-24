package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;

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
    @JsonView(QuizView.Before.class)
    private Long id;

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

    /**
     * tempID is needed to refer to drop locations that have not been persisted yet in the correctMappings of a question (so user can create mappings in the UI before saving new
     * drop locations)
     */
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
        return invalid == null ? false : invalid;
    }

    public DropLocation question(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
        return this;
    }

    public DropLocation invalid(Boolean invalid) {
        this.invalid = invalid;
        return this;
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

    public DropLocation mappings(Set<DragAndDropMapping> mappings) {
        this.mappings = mappings;
        return this;
    }

    public DropLocation addMappings(DragAndDropMapping mapping) {
        this.mappings.add(mapping);
        mapping.setDropLocation(this);
        return this;
    }

    public DropLocation removeMappings(DragAndDropMapping mapping) {
        this.mappings.remove(mapping);
        mapping.setDropLocation(null);
        return this;
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

        return ((correctDragItems.size() == 0 && selectedDragItem == null) || (selectedDragItem != null && correctDragItems.contains(selectedDragItem)));
        // this drop location was meant to stay empty and user didn't drag anything onto it
        // OR the user dragged one of the correct drag items onto this drop location
        // => this is correct => Return true;
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
        return "DropLocation{" + "id=" + getId() + ", posX='" + getPosX() + "'" + ", posY='" + getPosY() + "'" + ", width='" + getWidth() + "'" + ", height='" + getHeight() + "'"
                + ", invalid='" + isInvalid() + "'" + "}";
    }
}
