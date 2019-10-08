package de.tum.in.www1.artemis.service.compass.controller;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class ModelIndex {

    private Queue<UMLElement> uniqueModelElementList;

    /**
     * Note: The key is the model submission id
     */
    private Map<Long, UMLDiagram> modelMap;

    private Map<UMLElement, Integer> modelElementMapping;

    public ModelIndex() {
        modelElementMapping = new ConcurrentHashMap<>();
        uniqueModelElementList = new ConcurrentLinkedQueue<>();
        modelMap = new ConcurrentHashMap<>();
    }

    /**
     * Get the internal similarity id for the given model element. If the element is similar to an existing one, they share the same similarity id. Otherwise, a new id is created.
     *
     * @param element a model element for which the corresponding similarity id should be retrieved
     * @return the similarity id for the given model element
     */
    int retrieveSimilarityId(UMLElement element) {
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

    public void addModel(UMLDiagram model) {
        modelMap.put(model.getModelSubmissionId(), model);
    }

    public UMLDiagram getModel(long modelSubmissionId) {
        return modelMap.get(modelSubmissionId); // TODO MJ check if there? return Optional?
    }

    public Map<Long, UMLDiagram> getModelMap() {
        return modelMap;
    }

    public Collection<UMLDiagram> getModelCollection() {
        return modelMap.values();
    }

    int getModelCollectionSize() {
        return modelMap.size();
    }

    /**
     * Used for evaluation
     *
     * @return the number of unique model elements
     */
    public int getNumberOfUniqueElements() {
        return uniqueModelElementList.size();
    }

    /**
     * Used for evaluation
     *
     * @return the model element to similarity id mapping
     */
    public Map<UMLElement, Integer> getModelElementMapping() {
        return modelElementMapping;
    }

    public Collection<UMLElement> getUniqueElements() {
        return uniqueModelElementList;
    }
}
