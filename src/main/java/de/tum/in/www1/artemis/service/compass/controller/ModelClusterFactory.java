package de.tum.in.www1.artemis.service.compass.controller;

import static com.google.gson.JsonParser.parseString;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.modeling.ModelCluster;
import de.tum.in.www1.artemis.domain.modeling.ModelElement;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLAttribute;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLMethod;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class ModelClusterFactory {

    private final Logger log = LoggerFactory.getLogger(ModelClusterFactory.class);

    /**
     * Finds the similar elements among submissions and puts them in a cluster
     *
     * @param modelingSubmissions the submissions to build the clusters from
     * @param exercise the exercise that submissions belong to
     * @return an unmodifiable list of clusters that have more than one element in them
     */
    public List<ModelCluster> buildClusters(List<ModelingSubmission> modelingSubmissions, ModelingExercise exercise) {
        // The elements that has no other similar elements or are the first of their kind
        HashSet<UMLElement> uniqueElements = new HashSet<>();

        // The map of similarity id and clusters. We are using similarity id instead of cluster id here since clusters do not exist in database yet
        Map<Integer, ModelCluster> clusters = new ConcurrentHashMap<>();
        // TODO: this should work without unproxy!
        for (Submission submission : modelingSubmissions) {
            // We have to unproxy here as sometimes the Submission is a Hibernate proxy resulting in a cast exception
            // when iterating over the ModelingSubmissions directly (i.e. for (ModelingSubmission submission : submissions)).
            ModelingSubmission modelingSubmission = (ModelingSubmission) Hibernate.unproxy(submission);

            List<UMLElement> modelElements = getModelElements(modelingSubmission);
            if (modelElements != null) {
                for (UMLElement element : modelElements) {
                    selectCluster(element, uniqueElements, clusters, exercise, modelingSubmission);
                }
                setContextOfModelElements(modelElements);
            }
        }

        return clusters.values().stream().filter(modelCluster -> modelCluster.getModelElements().size() > 1).toList();
    }

    /**
     * Set the context of all model elements of the given UML diagram. For UML attributes and methods, the context contains the similarityId of their parent class. For all other
     * elements no context is considered and the default NO_CONTEXT is assigned.
     *
     * @param elements the model containing the model elements for which the context should be set
     */
    private static void setContextOfModelElements(List<UMLElement> elements) {
        Context context;

        for (UMLElement element : elements) {
            context = Context.NO_CONTEXT;

            if (element instanceof UMLAttribute attribute) {
                context = new Context(attribute.getParentElement().getSimilarityID());
            }
            else if (element instanceof UMLMethod method) {
                context = new Context(method.getParentElement().getSimilarityID());
            }

            element.setContext(context);
        }
    }

    /**
     * Builds and returns the elements of the modeling submission
     *
     * @param modelingSubmission the submission that has the elements
     * @return the uml elements that submission has
     */
    public List<UMLElement> getModelElements(ModelingSubmission modelingSubmission) {
        String modelString = modelingSubmission.getModel();
        if (modelString != null) {
            JsonObject modelObject = parseString(modelString).getAsJsonObject();
            try {
                UMLDiagram model = UMLModelParser.buildModelFromJSON(modelObject, modelingSubmission.getId());
                return model.getAllModelElements();
            }
            catch (IOException e) {
                log.error("Error while building and adding model!", e);
            }
        }
        return null;
    }

    /**
     * Builds and returns the elements of the modeling submission
     *
     * @param element the element to compare for other elements
     * @param uniqueModelElements the elements that have no similar elements or the first of their kind
     * @param clusters map of clusters and similarity ids to assign the element
     * @param exercise the exercise that submission of element belongs to
     * @param submission the submission that element belongs to
     */
    private void selectCluster(UMLElement element, Set<UMLElement> uniqueModelElements, Map<Integer, ModelCluster> clusters, ModelingExercise exercise,
            ModelingSubmission submission) {

        // Pair of similarity value and cluster ID
        var bestSimilarityFit = Pair.of(-1.0, -1);

        for (final var knownElement : uniqueModelElements) {
            final var similarity = knownElement.similarity(element);
            if (similarity > CompassConfiguration.EQUALITY_THRESHOLD && similarity > bestSimilarityFit.getFirst()) {
                // element is similar to existing element and has a higher similarity than another element
                bestSimilarityFit = Pair.of(similarity, knownElement.getSimilarityID());
            }
        }

        ModelCluster cluster;
        if (bestSimilarityFit.getFirst() != -1.0) {
            int similarityId = bestSimilarityFit.getSecond();
            element.setSimilarityID(similarityId);
            cluster = clusters.get(similarityId);
        }
        else {
            int similarityId = uniqueModelElements.size();
            cluster = new ModelCluster();
            cluster.setMinimumSimilarity(CompassConfiguration.EQUALITY_THRESHOLD);
            cluster.setModelElementType(element.getType());
            cluster.setExercise(exercise);
            clusters.put(similarityId, cluster);
            // element does not fit already known element / similarity set
            element.setSimilarityID(similarityId);
            uniqueModelElements.add(element);
        }
        ModelElement modelElement = new ModelElement();
        modelElement.setCluster(cluster);
        modelElement.setModelElementId(element.getJSONElementID());
        modelElement.setModelElementType(element.getType());
        modelElement.setSubmission(submission);
        // set knowledge to model element
        modelElement.setKnowledge(exercise.getKnowledge());
        cluster.addModelElement(modelElement);
    }
}
