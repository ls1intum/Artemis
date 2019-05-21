package de.tum.in.www1.artemis.service.compass.controller;

import java.util.Collection;

import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLAttribute;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassRelationship;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLMethod;

public class SimilarityDetector {

    /**
     * Determine elementId and context for each model element of a new model
     *
     * @param model the new model which contains the model elements
     * @param index the modelIndex which keeps track of all elementIds
     */
    public static void analyzeSimilarity(UMLClassDiagram model, ModelIndex index) {

        for (UMLClass umlClass : model.getClassList()) {
            umlClass.setSimilarityID(index.getElementID(umlClass));

            for (UMLAttribute attribute : umlClass.getAttributes()) {
                attribute.setSimilarityID(index.getElementID(attribute));
            }

            for (UMLMethod method : umlClass.getMethods()) {
                method.setSimilarityID(index.getElementID(method));
            }
        }

        for (UMLClassRelationship relation : model.getAssociationList()) {
            relation.setSimilarityID(index.getElementID(relation));
        }

        setContext(model);
    }

    private static void setContext(UMLClassDiagram model) {
        for (UMLClass umlClass : model.getClassList()) {
            umlClass.setContext(generateContextForElement(model, umlClass));
            for (UMLAttribute attribute : umlClass.getAttributes()) {
                attribute.setContext(generateContextForElement(model, attribute));
            }
            for (UMLMethod method : umlClass.getMethods()) {
                method.setContext(generateContextForElement(model, method));
            }
        }
        for (UMLClassRelationship relation : model.getAssociationList()) {
            relation.setContext(generateContextForElement(model, relation));
        }
    }

    // TODO: we need a very good documentation here
    private static Context generateContextForElement(UMLClassDiagram model, UMLElement element) {

        if (element.getClass() == UMLAttribute.class) {
            for (UMLClass umlClass : model.getClassList()) {
                if (umlClass.getAttributes().contains(element)) {
                    return new Context(umlClass.getSimilarityID());
                }
            }
        }
        else if (element.getClass() == UMLMethod.class) {
            for (UMLClass umlClass : model.getClassList()) {
                if (umlClass.getMethods().contains(element)) {
                    return new Context(umlClass.getSimilarityID());
                }
            }
        }

        /*
         * Do not use context for classes Class context reduces the automatic assessment rate significantly
         */

        /*
         * else if (element.getClass() == UMLClass.class) { return ClassContext.getWeakContext((UMLClass) element, model); } else if (element.getClass() == UMLAssociation.class) {
         * UMLAssociation relation = (UMLAssociation) element; Set<Integer> edges = new HashSet<>(); for (UMLClass connectableElement : model.getClassList()) { if
         * (relation.getSource().equals(connectableElement) || relation.getTarget().equals(connectableElement)) { edges.add(connectableElement.getSimilarityID()); } } if
         * (!edges.isEmpty()) { return new Context(edges); } }
         */

        return Context.NO_CONTEXT;
    }

    @SuppressWarnings("unused")
    static double diversity(Collection<UMLClassDiagram> modelList) {
        double diversity = 0;

        for (UMLClassDiagram model : modelList) {
            for (UMLClassDiagram referenceModel : modelList) {
                diversity += referenceModel.similarity(model);
            }
        }

        diversity /= Math.pow(modelList.size(), 2);

        return diversity;
    }
}
