package de.tum.cit.aet.artemis.service.compass.umlmodel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.tum.cit.aet.artemis.service.compass.umlmodel.deployment.UMLNode;

public abstract class UMLContainerElement extends UMLElement implements Serializable {

    private List<UMLElement> subElements = new ArrayList<>();

    public List<UMLElement> getSubElements() {
        return subElements;
    }

    public void setSubElements(List<UMLElement> subElements) {
        this.subElements = subElements;
    }

    /**
     * empty constructor used to make mockito happy
     */
    public UMLContainerElement() {
        // default empty constructor
    }

    public UMLContainerElement(String jsonElementID) {
        super(jsonElementID);
    }

    public UMLContainerElement(String jsonElementID, List<UMLElement> subElements) {
        super(jsonElementID);
        this.subElements = subElements;
        for (var subElement : subElements) {
            subElement.setParentElement(this);
        }
    }

    /**
     * Add a UML element to the list of sub elements in this container element
     *
     * @param umlElement the UML element that should be added to this container element
     */
    public void addSubElement(UMLElement umlElement) {
        this.subElements.add(umlElement);
        umlElement.setParentElement(umlElement);
    }

    /**
     * Remove a UML element from the list of sub elements in this container element.
     *
     * @param umlElement the UML element that should be removed from this container element
     */
    public void removeSubElement(UMLNode umlElement) {
        this.subElements.remove(umlElement);
        umlElement.setParentElement(null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), subElements);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLContainerElement otherElement = (UMLContainerElement) obj;

        return otherElement.subElements.size() == subElements.size() && otherElement.subElements.containsAll(subElements) && subElements.containsAll(otherElement.subElements);
    }
}
