package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.TempIdObject;

/**
 * A DropLocation.
 */
public class DropLocation extends TempIdObject implements QuizQuestionComponent<DragAndDropQuestion>, Serializable {

    private Double posX;

    private Double posY;

    private Double width;

    private Double height;

    private Boolean invalid = false;

    @Override
    public void setQuestion(DragAndDropQuestion quizQuestion) {

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

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    @Override
    public String toString() {
        return "DropLocation{" + "id=" + getId() + ", posX='" + getPosX() + "'" + ", posY='" + getPosY() + "'" + ", width='" + getWidth() + "'" + ", height='" + getHeight() + "'"
                + ", invalid='" + isInvalid() + "'" + "}";
    }
}
