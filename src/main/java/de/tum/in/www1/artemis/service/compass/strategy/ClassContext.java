package de.tum.in.www1.artemis.service.compass.strategy;

import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLAssociation;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLModel;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("unused")
public class ClassContext {

    public static Context getNoContext(UMLClass umlClass, UMLModel model) {
        return Context.NO_CONTEXT;
    }

    /**
     *
     * @return Context including the relations connected to the UML class and all children of the class (attributes + methods)
     */
    public static Context getStrictContext(UMLClass umlClass, UMLModel model) {
        Set<Integer> associations = findAssociationsForClassInModel(umlClass, model);
        for (UMLElement element: umlClass.getAttributes()) {
            associations.add(element.getElementID());
        }
        for (UMLElement element: umlClass.getMethods()) {
            associations.add(element.getElementID());
        }
        if (associations.isEmpty()) {
            return Context.NO_CONTEXT;
        }
        return new Context(associations);
    }

    private static Set<Integer> findAssociationsForClassInModel(UMLClass umlClass, UMLModel model) {
        Set<Integer> relations = new HashSet<>();
        for (UMLAssociation umlAssociation : model.getAssociationList()) {
            if (umlAssociation.getSource().equals(umlClass) || umlAssociation.getTarget().equals(umlClass)) {
                relations.add(umlAssociation.getElementID());
            }
        }
        return relations;
    }

    /**
     *
     * @return Context including the relations connected to the UML class
     */
    public static Context getWeakContext(UMLClass umlClass, UMLModel model) {
        Set<Integer> associations = findAssociationsForClassInModel(umlClass, model);
        if (associations.isEmpty()) {
            return Context.NO_CONTEXT;
        }
        return new Context(associations);
    }
}
