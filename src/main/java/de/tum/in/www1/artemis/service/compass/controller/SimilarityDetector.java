package de.tum.in.www1.artemis.service.compass.controller;

import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.strategy.ClassContext;
import de.tum.in.www1.artemis.service.compass.umlmodel.*;

import java.util.Collection;
import java.util.HashSet;

public class SimilarityDetector {

    public static void analyzeSimilarity(UMLModel model, ModelIndex index) {

        // TODO: parse connectables

        for (UMLClass umlClass : model.getConnectableList()) {
            umlClass.setElementID(index.getElementID(umlClass));

            for (UMLAttribute attribute : umlClass.getAttributeList()) {
                attribute.setElementID(index.getElementID(attribute));
            }

            for (UMLMethod method : umlClass.getMethodList()) {
                method.setElementID(index.getElementID(method));
            }
        }

        for (UMLRelation relation : model.getRelationList()) {
            relation.setElementID(index.getElementID(relation));
        }

        analyzeSimilarity(model);
    }

    private static void analyzeSimilarity(UMLModel model) {
        for (UMLClass umlClass : model.getConnectableList()) {
            umlClass.setContext(generateContextForElement(model, umlClass));
            for (UMLAttribute attribute : umlClass.getAttributeList()) {
                attribute.setContext(generateContextForElement(model, attribute));
            }
            for (UMLMethod method : umlClass.getMethodList()) {
                method.setContext(generateContextForElement(model, method));
            }
        }
        for (UMLRelation relation : model.getRelationList()) {
            relation.setContext(generateContextForElement(model, relation));
        }
    }

    private static Context generateContextForElement(UMLModel model, UMLElement element) {

        if (element.getClass() == UMLAttribute.class) {
            for (UMLClass umlClass : model.getConnectableList()) {
                if (umlClass.getAttributeList().contains(element)) {
                    return new Context(umlClass.getElementID());
                }
            }
        }
        else if (element.getClass() == UMLMethod.class) {
            for (UMLClass umlClass : model.getConnectableList()) {
                if (umlClass.getMethodList().contains(element)) {
                    return new Context(umlClass.getElementID());
                }
            }
        }
        else if (element.getClass() == UMLClass.class) {
            return ClassContext.getWeakContext((UMLClass) element, model);
        }
        else if (element.getClass() == UMLRelation.class) {
            UMLRelation relation = (UMLRelation) element;
            HashSet<Integer> edges = new HashSet<>();
            for (UMLClass connectableElement : model.getConnectableList()) {
                if (relation.getSource().equals(connectableElement) || relation.getTarget().equals(connectableElement)) {
                    edges.add(connectableElement.getElementID());
                }
            }
            if (!edges.isEmpty()) {
                return new Context(edges);
            }
        }

        return Context.NO_CONTEXT;
    }

    public static double diversity (Collection<UMLModel> modelList) {
        double diversity = 0;

        for (UMLModel model : modelList) {
            for (UMLModel referenceModel : modelList) {
                diversity += referenceModel.similarity(model);
            }
        }

        diversity /= Math.pow(modelList.size(), 2);

        return diversity;
    }
}
