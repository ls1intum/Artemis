package de.tum.cit.aet.artemis.service.compass.umlmodel.object;

import java.util.List;

import de.tum.cit.aet.artemis.service.compass.umlmodel.classdiagram.UMLMethod;

public class UMLObjectMethod extends UMLMethod {

    public static final String UML_METHOD_TYPE = "ObjectMethod";

    /**
     * empty constructor used to make mockito happy
     */
    public UMLObjectMethod() {
        // default empty constructor
    }

    public UMLObjectMethod(String completeName, String name, String returnType, List<String> parameters, String jsonElementID) {
        super(completeName, name, returnType, parameters, jsonElementID);
    }

    @Override
    public String getType() {
        return UML_METHOD_TYPE;
    }

}
