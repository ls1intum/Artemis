package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class TempIdObject implements Serializable {

    private Long id;

    /**
     * tempID is needed to refer to objects that have not been persisted yet (so user can create and connect those in the UI before saving them)
     */
    @Transient
    // variable name must be different from Getter name, so that Jackson ignores the @Transient annotation, but Hibernate still respects it
    private Long tempIDTransient;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTempID() {
        return tempIDTransient;
    }

    public void setTempID(Long tempID) {
        this.tempIDTransient = tempID;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    /**
     * checks the tempId first and then the database id
     *
     * @param obj another object
     * @return true when the tempIds are equal or the ids are equal
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TempIdObject tempIdObject = (TempIdObject) obj;
        if (tempIdObject.getTempID() != null && getTempID() != null && Objects.equals(getTempID(), tempIdObject.getTempID())) {
            return true;
        }

        if (tempIdObject.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), tempIdObject.getId());
    }
}
