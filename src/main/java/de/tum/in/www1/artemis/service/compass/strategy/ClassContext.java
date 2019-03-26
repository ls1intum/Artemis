package de.tum.in.www1.artemis.service.compass.strategy;

import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassModel;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassRelationship;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("unused")
public class ClassContext {

    public static Context getNoContext(UMLClass umlClass, UMLClassModel model) {
        return Context.NO_CONTEXT;
    }

    /**
     *
     * @return Context including the relations connected to the UML class and all children of the class (attributes + methods)
     */
    public static Context getStrictContext(UMLClass umlClass, UMLClassModel model) {
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

    private static Set<Integer> findAssociationsForClassInModel(UMLClass umlClass, UMLClassModel model) {
        Set<Integer> relations = new HashSet<>();
        for (UMLClassRelationship relationship : model.getAssociationList()) {
            if (relationship.getSource().equals(umlClass) || relationship.getTarget().equals(umlClass)) {
                relations.add(relationship.getElementID());
            }
        }
        return relations;
    }

    /**
     *
     * @return Context including the relations connected to the UML class
     */
    public static Context getWeakContext(UMLClass umlClass, UMLClassModel model) {
        Set<Integer> associations = findAssociationsForClassInModel(umlClass, model);
        if (associations.isEmpty()) {
            return Context.NO_CONTEXT;
        }
        return new Context(associations);
    }
}
