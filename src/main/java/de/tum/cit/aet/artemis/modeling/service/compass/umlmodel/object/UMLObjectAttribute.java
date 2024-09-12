package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.object;

import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.classdiagram.UMLAttribute;

public class UMLObjectAttribute extends UMLAttribute {

    public static final String UML_ATTRIBUTE_TYPE = "ObjectAttribute";

    /**
     * empty constructor used to make mockito happy
     */
    public UMLObjectAttribute() {
        // default empty constructor
    }

    public UMLObjectAttribute(String name, String attributeType, String jsonElementID) {
        super(name, attributeType, jsonElementID);
    }

    @Override
    public String getType() {
        return UML_ATTRIBUTE_TYPE;
    }
}
