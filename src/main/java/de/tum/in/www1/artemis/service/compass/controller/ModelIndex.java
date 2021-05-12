package de.tum.in.www1.artemis.service.compass.controller;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.springframework.data.util.Pair;

import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class ModelIndex {

    private IMap<String, Long> uniqueModelElementList;

    /**
     * Note: The key is the model submission id
     */
    private IMap<Long, UMLDiagram> modelMap;

    private IMap<String, Integer> elementSimilarityMap;

    private IMap<String, String> elementTypeMap;

    /**
     * Pending models that are submitted by users but did not get converted into Java classes to not effect submission time during exams
     */
    public Map<Long, String> pendingModels;

    public ModelIndex(Long exerciseId, HazelcastInstance hazelcastInstance) {
        elementSimilarityMap = hazelcastInstance.getMap("similarities - " + exerciseId);
        uniqueModelElementList = hazelcastInstance.getMap("elements - " + exerciseId);

        MapConfig uniqueModelElementListConfig = new MapConfig("elements - " + exerciseId);
        uniqueModelElementListConfig.setNearCacheConfig(getNearCacheConfig("models - " + exerciseId));
        hazelcastInstance.getConfig().addMapConfig(uniqueModelElementListConfig);

        modelMap = hazelcastInstance.getMap("models - " + exerciseId);
        elementTypeMap = hazelcastInstance.getMap("element-types - " + exerciseId);
        pendingModels = hazelcastInstance.getMap(("pending-models - " + exerciseId));
    }

    /**
     * Get the internal similarity ID for the given model element. If the element is similar to an existing one, they share the same similarity id, i.e. they are in the same
     * similarity set. Otherwise, the given element does not belong to an existing similarity set and a new similarity ID is created for the element.
     *
     * @param element a model element for which the corresponding similarity ID should be retrieved
     * @return the similarity ID for the given model element, i.e. the ID of the similarity set the element belongs to
     */
    int retrieveSimilarityId(UMLElement element, UMLDiagram model) {
        String jsonElementId = element.getJSONElementID();
        if (elementSimilarityMap.containsKey(jsonElementId)) {
            return elementSimilarityMap.get(jsonElementId);
        }

        if (!elementTypeMap.containsKey(jsonElementId)) {
            elementTypeMap.set(jsonElementId, element.getType());
        }

        // Pair of similarity value and similarity ID
        var bestSimilarityFit = Pair.of(-1.0, -1);

        for (final var knownElementEntry : uniqueModelElementList.entrySet()) {
            UMLDiagram modelToGetElement;
            if (model.getModelSubmissionId() == knownElementEntry.getValue()) {
                modelToGetElement = model;
            }
            else {
                modelToGetElement = modelMap.get(knownElementEntry.getValue());
            }
            UMLElement knownElement = modelToGetElement.getElementByJSONID(knownElementEntry.getKey());
            final var similarity = knownElement.similarity(element);
            if (similarity > CompassConfiguration.EQUALITY_THRESHOLD && similarity > bestSimilarityFit.getFirst()) {
                // element is similar to existing element and has a higher similarity than another element
                bestSimilarityFit = Pair.of(similarity, getSimilarityId(knownElementEntry.getKey()));
            }
        }

        if (bestSimilarityFit.getFirst() != -1.0) {
            int similarityId = bestSimilarityFit.getSecond();
            element.setSimilarityID(similarityId);
            elementSimilarityMap.set(element.getJSONElementID(), similarityId);
            return similarityId;
        }

        // element does not fit already known element / similarity set
        int similarityId = uniqueModelElementList.size();
        element.setSimilarityID(similarityId);
        uniqueModelElementList.set(element.getJSONElementID(), model.getModelSubmissionId());

        elementSimilarityMap.set(element.getJSONElementID(), similarityId);
        return similarityId;
    }

    /**
     * Add a new model to the model map.
     *
     * @param model the new model that should be added
     */
    public void addModel(UMLDiagram model) {
        modelMap.set(model.getModelSubmissionId(), model);
    }

    /**
     * Get the model that belongs to the given submission ID from the model map.
     *
     * @param modelSubmissionId the ID of the submission to which the requested model belongs to
     * @return the model that belong to the submission with the given ID
     */
    public UMLDiagram getModel(long modelSubmissionId) {
        return modelMap.get(modelSubmissionId);
    }

    /**
     * Get the collection of all the models.
     *
     * @return the collection of models
     */
    public Collection<UMLDiagram> getModelCollection() {
        return modelMap.values();
    }

    /**
     * Get the number of models.
     *
     * @return the number of models
     */
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
    public Map<String, Integer> getElementSimilarityMap() {
        return elementSimilarityMap;
    }

    /**
     * Get the collection of unique elements. Each unique element represents a similarity set.
     *
     * @return the collection of unique elements
     */
    public Set<Map.Entry<String, Long>> getUniqueElementEntries() {
        return uniqueModelElementList.entrySet();
    }

    /**
     * Get the similarity id of element with given jsonElementId
     *
     * @return similarity id
     */
    public Integer getSimilarityId(String jsonElementId) {
        return elementSimilarityMap.get(jsonElementId);
    }

    /**
     * Get the entries in model map
     *
     * @return entry set of model map
     */
    public Set<Map.Entry<Long, UMLDiagram>> getModelEntries() {
        return modelMap.entrySet();
    }

    /**
     *  Check if given submission exists
     *
     * @return whether model map contains given id as key
     */
    public Boolean modelExists(long modelSubmissionId) {
        return modelMap.containsKey(modelSubmissionId);
    }

    /**
     * Get the collection of model ids
     *
     * @return the collection of model id
     */
    public Collection<Long> getModelIds() {
        return modelMap.keySet();
    }

    public void addPendingModel(long modelId, String model) {
        pendingModels.put(modelId, model);
    };

    public void removePendingModel(long modelId) {
        pendingModels.remove(modelId);
    };

    public Set<Map.Entry<Long, String>> getPendingEntries() {
        return pendingModels.entrySet();
    }

    public void destroy() {
        modelMap.destroy();
        elementTypeMap.destroy();
        elementSimilarityMap.destroy();
        uniqueModelElementList.destroy();
    }

    public NearCacheConfig getNearCacheConfig(String cacheName) {
        EvictionConfig evictionConfig = new EvictionConfig() //
                .setEvictionPolicy(EvictionPolicy.NONE);

        NearCacheConfig nearCacheConfig = new NearCacheConfig() //
                .setName(cacheName + "-local") //
                .setInMemoryFormat(InMemoryFormat.OBJECT) //
                .setSerializeKeys(true) //
                .setInvalidateOnChange(true) //
                .setTimeToLiveSeconds(0) //
                .setMaxIdleSeconds(0) //
                .setEvictionConfig(evictionConfig) //
                .setCacheLocalEntries(true);
        return nearCacheConfig;
    }

}
