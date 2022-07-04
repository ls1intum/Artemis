package de.tum.in.www1.artemis.domain;

import java.util.Objects;

import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class TempIdObject extends DomainObject {

    /**
     * tempID is needed to refer to objects that have not been persisted yet (so user can create and connect those in the UI before saving them)
     */
    @Transient
    // variable name must be different from Getter name, so that Jackson ignores the @Transient annotation, but Hibernate still respects it
    private Long tempIDTransient;

    public Long getTempID() {
        return tempIDTransient;
    }

    public void setTempID(Long tempID) {
        this.tempIDTransient = tempID;
    }

    /**
     * checks the tempId first and then the database id
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
        return super.equals(obj);
    }
}
