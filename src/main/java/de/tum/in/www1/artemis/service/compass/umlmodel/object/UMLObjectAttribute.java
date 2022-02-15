package de.tum.in.www1.artemis.service.compass.umlmodel.object;

import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLAttribute;

public class UMLObjectAttribute extends UMLAttribute {

    public static final String UML_ATTRIBUTE_TYPE = "ObjectAttribute";

    /**
     * empty constructor used to make mockito happy
     */
    public UMLObjectAttribute() {
        super();
    }

    public UMLObjectAttribute(String name, String attributeType, String jsonElementID) {
        super(name, attributeType, jsonElementID);
    }

    @Override
    public String getType() {
        return UML_ATTRIBUTE_TYPE;
    }
}
