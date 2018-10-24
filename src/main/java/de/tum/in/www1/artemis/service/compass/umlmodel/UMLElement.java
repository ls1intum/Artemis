package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.assessment.Context;

import java.util.Objects;

public abstract class UMLElement {

    int elementID;
    String jsonElementID;
    private Context context;

    /**
     * Compare this with another element to calculate the similarity
     *
     * @param element the element to compare with
     * @return the similarity as number [0-1]
     */
    public abstract double similarity(UMLElement element);

    public abstract String getName();

    public int getElementID() {
        return elementID;
    }

    public void setElementID (int elementID) {
        this.elementID = elementID;
    }

    public String getJSONElementID() {
        return jsonElementID;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        UMLElement otherElement = (UMLElement) obj;

        if (otherElement.context == null || this.context == null) {
            return otherElement.elementID == this.elementID && otherElement.jsonElementID.equals(this.jsonElementID)
                && otherElement.context == this.context;
        }

        return otherElement.elementID == this.elementID && otherElement.jsonElementID.equals(this.jsonElementID)
            && otherElement.context.equals(this.context);
    }

    @Override
    public int hashCode () {
        return Objects.hash(elementID, jsonElementID, context);
    }
}
