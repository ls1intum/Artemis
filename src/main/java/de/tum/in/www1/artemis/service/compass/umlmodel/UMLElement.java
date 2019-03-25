package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.assessment.Context;

import java.util.Objects;

public abstract class UMLElement {

    private int similarityID; //id of similarity set the element belongs to
    private String jsonElementID; // unique element id //TODO rename into uniqueId?
    private Context context;

    /**
     * Compare this with another element to calculate the similarity
     *
     * @param element the element to compare with
     * @return the similarity as number [0-1]
     */
    public abstract double similarity(UMLElement element);

    public abstract String getName();

    public abstract String getValue();

    public int getElementID() {
        return similarityID;
    }

    public void setElementID (int elementID) {
        this.similarityID = elementID;
    }

    public String getJSONElementID() {
        return jsonElementID;
    }

    public void setJsonElementID(String jsonElementID) {
        this.jsonElementID = jsonElementID;
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
            return otherElement.similarityID == this.similarityID && otherElement.jsonElementID.equals(this.jsonElementID)
                && otherElement.context == this.context;
        }

        return otherElement.similarityID == this.similarityID && otherElement.jsonElementID.equals(this.jsonElementID)
            && otherElement.context.equals(this.context);
    }

    @Override
    public int hashCode () {
        return Objects.hash(similarityID, jsonElementID, context);
    }
}
