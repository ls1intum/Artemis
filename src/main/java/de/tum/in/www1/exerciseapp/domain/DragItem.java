package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

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

    @Column(name = "correct_score")
    private Integer correctScore;

    @Column(name = "incorrect_score")
    private Integer incorrectScore;

    @OneToOne
    @JoinColumn(unique = true)
    private DropLocation correctLocation;

    @ManyToOne
    @JsonIgnoreProperties({"dragItems", "dropLocations"})
    private DragAndDropQuestion question;

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove
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

    public Integer getCorrectScore() {
        return correctScore;
    }

    public DragItem correctScore(Integer correctScore) {
        this.correctScore = correctScore;
        return this;
    }

    public void setCorrectScore(Integer correctScore) {
        this.correctScore = correctScore;
    }

    public Integer getIncorrectScore() {
        return incorrectScore;
    }

    public DragItem incorrectScore(Integer incorrectScore) {
        this.incorrectScore = incorrectScore;
        return this;
    }

    public void setIncorrectScore(Integer incorrectScore) {
        this.incorrectScore = incorrectScore;
    }

    public DropLocation getCorrectLocation() {
        return correctLocation;
    }

    public DragItem correctLocation(DropLocation dropLocation) {
        this.correctLocation = dropLocation;
        return this;
    }

    public void setCorrectLocation(DropLocation dropLocation) {
        this.correctLocation = dropLocation;
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
    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DragItem dragItem = (DragItem) o;
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
            ", correctScore='" + getCorrectScore() + "'" +
            ", incorrectScore='" + getIncorrectScore() + "'" +
            "}";
    }
}
