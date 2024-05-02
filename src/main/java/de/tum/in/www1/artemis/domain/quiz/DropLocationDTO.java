package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;

/**
 * A DropLocation.
 */
public class DropLocationDTO implements Serializable {

    private Long id;

    private Double posX;

    private Double posY;

    private Double width;

    private Double height;

    private Boolean invalid = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getPosX() {
        return posX;
    }

    public DropLocationDTO posX(Double posX) {
        this.posX = posX;
        return this;
    }

    public void setPosX(Double posX) {
        this.posX = posX;
    }

    public Double getPosY() {
        return posY;
    }

    public DropLocationDTO posY(Double posY) {
        this.posY = posY;
        return this;
    }

    public void setPosY(Double posY) {
        this.posY = posY;
    }

    public Double getWidth() {
        return width;
    }

    public DropLocationDTO width(Double width) {
        this.width = width;
        return this;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public Double getHeight() {
        return height;
    }

    public DropLocationDTO height(Double height) {
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
