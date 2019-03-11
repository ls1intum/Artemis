package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.service.compass.grade.Grade;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface CalculationEngine {

    Map.Entry<Long, Grade> getNextOptimalModel();

    /**
     * Get the assessment result for a model. If no assessment is saved for the given model, it tries
     * to create a new one automatically with the existing information of the engine.
     *
     * @param modelId the id of the model
     * @return the assessment result for the model
     */
    Grade getResultForModel(long modelId);

    Collection<Long> getModelIds();

    /**
     * Update the engine with a new manual assessment.
     *
     * @param modelingAssessment the new assessment as list of individual Feedback objects
     * @param submissionId       the id of the corresponding model
     */
    void notifyNewAssessment(List<Feedback> modelingAssessment, long submissionId);

    /**
     * Add a new model
     *
     * @param model   the new model as raw sting
     * @param modelId the id of the new model
     */
    void notifyNewModel(String model, long modelId);

    /**
     * @return the time when the engine has been used last
     */
    LocalDateTime getLastUsedAt();

    Map<Long, Grade> getModelsWaitingForAssessment();

    void removeModelWaitingForAssessment(long modelId, boolean isAssessed);

    List<Feedback> convertToFeedback(Grade grade, long modelId);

    /**
     * @return statistics about the UML model
     */
    JsonObject getStatistics();

}
