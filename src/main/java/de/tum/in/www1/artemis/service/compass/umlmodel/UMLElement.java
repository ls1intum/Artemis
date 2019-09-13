package de.tum.in.www1.artemis.service.compass.umlmodel;

import java.util.Objects;

import de.tum.in.www1.artemis.service.compass.assessment.Context;

public abstract class UMLElement implements SimilarElement<UMLElement>{

    private int similarityID; // id of similarity set the element belongs to

    private String jsonElementID; // unique element id //TODO rename into uniqueId?

    private Context context;

    /**
     * Compare this with another UML element to calculate the similarity. If the types of the two elements do not match (e.g. comparing a UML class with a UML activity node), the
     * returned similarity score is 0.
     *
     * @param element the element to compare with
     * @return the similarity as number [0-1]
     */
    @Override
    public abstract double similarity(UMLElement element);

    /**
     * Get the name of the UML element. If the element type has no name (e.g. UMLRelationship), the element type is returned instead.
     *
     * @return the name of the UML element, or the type of the element if no name attribute exists for the element type
     */
    public abstract String getName();

    /**
     * Get the type of the UML element as string. IMPORTANT: It should be the same as the type attribute of the respective UML elements used in the JSON representation of the
     * models created by Apollon.
     *
     * @return the type of the UML element
     */
    public abstract String getType();

    public int getSimilarityID() {
        return similarityID;
    }

    public void setSimilarityID(int similarityID) {
        this.similarityID = similarityID;
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
    public abstract String toString();

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
            return otherElement.similarityID == this.similarityID && otherElement.jsonElementID.equals(this.jsonElementID) && otherElement.context == this.context;
        }

        return otherElement.similarityID == this.similarityID && otherElement.jsonElementID.equals(this.jsonElementID) && otherElement.context.equals(this.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(similarityID, jsonElementID, context);
    }
}
