package de.tum.in.www1.artemis.service.compass.umlmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.tum.in.www1.artemis.service.compass.assessment.Context;

import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")//Mapping to ModelElementTypes in Client
@JsonSubTypes({
    @JsonSubTypes.Type(value = UMLClass.class, name = "class"),
    @JsonSubTypes.Type(value = UMLAttribute.class, name = "attribute"),
    @JsonSubTypes.Type(value = UMLMethod.class, name = "method"),
    @JsonSubTypes.Type(value = UMLAssociation.class, name = "relationship")
})
public abstract class UMLElement {

    int elementID; //id of similarity set the element belongs to
    @JsonProperty("id")
    String jsonElementID; // unique element id //TODO rename into uniqueId?
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
