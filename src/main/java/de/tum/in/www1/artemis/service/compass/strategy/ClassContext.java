package de.tum.in.www1.artemis.service.compass.strategy;

import java.util.HashSet;
import java.util.Set;

import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassRelationship;

@SuppressWarnings("unused")
public class ClassContext {

    public static Context getNoContext(UMLClass umlClass, UMLClassDiagram model) {
        return Context.NO_CONTEXT;
    }

    /**
     * @return Context including the relations connected to the UML class and all children of the class (attributes + methods)
     */
    public static Context getStrictContext(UMLClass umlClass, UMLClassDiagram model) {
        Set<Integer> associations = findAssociationsForClassInModel(umlClass, model);
        for (UMLElement element : umlClass.getAttributes()) {
            associations.add(element.getSimilarityID());
        }
        for (UMLElement element : umlClass.getMethods()) {
            associations.add(element.getSimilarityID());
        }
        if (associations.isEmpty()) {
            return Context.NO_CONTEXT;
        }
        return new Context(associations);
    }

    private static Set<Integer> findAssociationsForClassInModel(UMLClass umlClass, UMLClassDiagram model) {
        Set<Integer> relations = new HashSet<>();
        for (UMLClassRelationship relationship : model.getAssociationList()) {
            if (relationship.getSource().equals(umlClass) || relationship.getTarget().equals(umlClass)) {
                relations.add(relationship.getSimilarityID());
            }
        }
        return relations;
    }

    /**
     * @return Context including the relations connected to the UML class
     */
    public static Context getWeakContext(UMLClass umlClass, UMLClassDiagram model) {
        Set<Integer> associations = findAssociationsForClassInModel(umlClass, model);
        if (associations.isEmpty()) {
            return Context.NO_CONTEXT;
        }
        return new Context(associations);
    }
}
