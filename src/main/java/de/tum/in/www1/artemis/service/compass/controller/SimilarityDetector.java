package de.tum.in.www1.artemis.service.compass.controller;

import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLAttribute;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassModel;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassRelationship;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLMethod;

import java.util.Collection;

public class SimilarityDetector {

    /**
     * Determine elementId and context for each model element of a new model
     *
     * @param model the new model which contains the model elements
     * @param index the modelIndex which keeps track of all elementIds
     */
    public static void analyzeSimilarity(UMLClassModel model, ModelIndex index) {

        for (UMLClass umlClass : model.getClassList()) {
            umlClass.setElementID(index.getElementID(umlClass));

            for (UMLAttribute attribute : umlClass.getAttributes()) {
                attribute.setElementID(index.getElementID(attribute));
            }

            for (UMLMethod method : umlClass.getMethods()) {
                method.setElementID(index.getElementID(method));
            }
        }

        for (UMLClassRelationship relation : model.getAssociationList()) {
            relation.setElementID(index.getElementID(relation));
        }

        setContext(model);
    }

    private static void setContext(UMLClassModel model) {
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


    //TODO: we need a very good documentation here
    private static Context generateContextForElement(UMLClassModel model, UMLElement element) {

        if (element.getClass() == UMLAttribute.class) {
            for (UMLClass umlClass : model.getClassList()) {
                if (umlClass.getAttributes().contains(element)) {
                    return new Context(umlClass.getElementID());
                }
            }
        }
        else if (element.getClass() == UMLMethod.class) {
            for (UMLClass umlClass : model.getClassList()) {
                if (umlClass.getMethods().contains(element)) {
                    return new Context(umlClass.getElementID());
                }
            }
        }

        /*
         * Do not use context for classes
         * Class context reduces the automatic assessment rate significantly
         */

        /*else if (element.getClass() == UMLClass.class) {
            return ClassContext.getWeakContext((UMLClass) element, model);
        }
        else if (element.getClass() == UMLAssociation.class) {
            UMLAssociation relation = (UMLAssociation) element;
            Set<Integer> edges = new HashSet<>();
            for (UMLClass connectableElement : model.getClassList()) {
                if (relation.getSource().equals(connectableElement) || relation.getTarget().equals(connectableElement)) {
                    edges.add(connectableElement.getElementID());
                }
            }
            if (!edges.isEmpty()) {
                return new Context(edges);
            }
        }*/

        return Context.NO_CONTEXT;
    }

    @SuppressWarnings("unused")
    static double diversity (Collection<UMLClassModel> modelList) {
        double diversity = 0;

        for (UMLClassModel model : modelList) {
            for (UMLClassModel referenceModel : modelList) {
                diversity += referenceModel.similarity(model);
            }
        }

        diversity /= Math.pow(modelList.size(), 2);

        return diversity;
    }
}
