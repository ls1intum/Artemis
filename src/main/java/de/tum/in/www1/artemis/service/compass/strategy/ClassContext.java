package de.tum.in.www1.artemis.service.compass.strategy;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLRelationship;

@SuppressWarnings("unused")
public class ClassContext {

    public static Context getNoContext(UMLClass umlClass, UMLClassDiagram model) {
        return Context.NO_CONTEXT;
    }

    /**
     * Finds all relationships for a UML class inside a class diagram including all similarity IDs
     *
     * @param model The class diagram in which to search for relationships
     * @param umlClass The class which should be searched for inside the diagram
     * @return Context including the relations connected to the UML class and all children of the class (attributes + methods)
     */
    public static Context getStrictContext(UMLClass umlClass, UMLClassDiagram model) {
        Set<Integer> relationships = findRelationshipsForClassInModel(umlClass, model);
        for (UMLElement element : umlClass.getAttributes()) {
            relationships.add(element.getSimilarityID());
        }
        for (UMLElement element : umlClass.getMethods()) {
            relationships.add(element.getSimilarityID());
        }
        if (relationships.isEmpty()) {
            return Context.NO_CONTEXT;
        }
        return new Context(relationships);
    }

    private static Set<Integer> findRelationshipsForClassInModel(UMLClass umlClass, UMLClassDiagram model) {
        Set<Integer> relations = ConcurrentHashMap.newKeySet();
        for (UMLRelationship relationship : model.getRelationshipList()) {
            if (relationship.getSource().equals(umlClass) || relationship.getTarget().equals(umlClass)) {
                relations.add(relationship.getSimilarityID());
            }
        }
        return relations;
    }

    /**
     * Finds all relationships for a UML class inside a class diagram
     *
     * @param umlClass The class which should be searched for inside the diagram
     * @param model The class diagram in which to search for relationships
     * @return Context including the relations connected to the UML class
     */
    public static Context getWeakContext(UMLClass umlClass, UMLClassDiagram model) {
        Set<Integer> relationships = findRelationshipsForClassInModel(umlClass, model);
        if (relationships.isEmpty()) {
            return Context.NO_CONTEXT;
        }
        return new Context(relationships);
    }
}
