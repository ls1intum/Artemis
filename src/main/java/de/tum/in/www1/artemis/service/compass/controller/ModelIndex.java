package de.tum.in.www1.artemis.service.compass.controller;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassDiagram;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class ModelIndex {

    private Queue<UMLElement> uniqueModelElementList;

    /**
     * Note: The key is the model submission id
     */
    private Map<Long, UMLClassDiagram> modelMap;

    private HashMap<UMLElement, Integer> modelElementMapping;

    public ModelIndex() {
        modelElementMapping = new HashMap<>();
        uniqueModelElementList = new ConcurrentLinkedQueue<>();
        modelMap = new HashMap<>();
    }

    /**
     * Get the internal "similarity" id for a model element. If the element is similar to an existing one, they share the same id.
     *
     * @param element an element of a model
     * @return its similarity id
     */
    int getSimilarityId(UMLElement element) {
        if (modelElementMapping.containsKey(element)) {
            return modelElementMapping.get(element);
        }
        // element is similar to existing element
        for (UMLElement knownElement : uniqueModelElementList) {
            if (knownElement.similarity(element) > CompassConfiguration.EQUALITY_THRESHOLD) {
                modelElementMapping.put(element, knownElement.getSimilarityID());
                return knownElement.getSimilarityID();
            }
        }
        // element does not fit already known element
        uniqueModelElementList.add(element);
        modelElementMapping.put(element, uniqueModelElementList.size() - 1);
        return uniqueModelElementList.size() - 1;
    }

    public void addModel(UMLClassDiagram model) {
        modelMap.put(model.getModelSubmissionId(), model);
    }

    public UMLClassDiagram getModel(long modelSubmissionId) {
        return modelMap.get(modelSubmissionId); // TODO MJ check if there? return Optional?
    }

    public Map<Long, UMLClassDiagram> getModelMap() {
        return modelMap;
    }

    public Collection<UMLClassDiagram> getModelCollection() {
        return modelMap.values();
    }

    int getModelCollectionSize() {
        return modelMap.size();
    }

    /**
     * Used for evaluation
     */
    public int getNumberOfUniqueElements() {
        return this.uniqueModelElementList.size();
    }

    public Collection<UMLElement> getUniqueElements() {
        return uniqueModelElementList;
    }
}
