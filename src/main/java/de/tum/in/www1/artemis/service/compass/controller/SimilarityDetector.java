package de.tum.in.www1.artemis.service.compass.controller;

import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLAttribute;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLMethod;

public class SimilarityDetector {

    /**
     * Analyze the similarity of all model elements of the given UML diagram. It gets the similarityId for every model element from the model index and assigns it to the
     * corresponding element. Additionally, it sets the context of the model elements.
     *
     * @param model the model which contains the model elements for which the similarityId and the context should be analyzed and set
     * @param index the modelIndex which keeps track of all similarityIds of all the model elements in one modeling exercise
     */
    public static void analyzeSimilarity(UMLDiagram model, ModelIndex index) {

        for (UMLElement element : model.getAllModelElements()) {
            index.retrieveSimilarityId(element);
        }

        setContextOfModelElements(model);
    }

    /**
     * Set the context of all model elements of the given UML diagram. For UML attributes and methods, the context contains the similarityId of their parent class. For all other
     * elements no context is considered and the default NO_CONTEXT is assigned.
     *
     * @param model the model containing the model elements for which the context should be set
     */
    private static void setContextOfModelElements(UMLDiagram model) {
        Context context;

        for (UMLElement element : model.getAllModelElements()) {
            context = Context.NO_CONTEXT;

            if (element instanceof UMLAttribute) {
                UMLAttribute attribute = (UMLAttribute) element;
                context = new Context(attribute.getParentElement().getSimilarityID());
            }
            else if (element instanceof UMLMethod) {
                UMLMethod method = (UMLMethod) element;
                context = new Context(method.getParentElement().getSimilarityID());
            }

            element.setContext(context);
        }
    }
}
