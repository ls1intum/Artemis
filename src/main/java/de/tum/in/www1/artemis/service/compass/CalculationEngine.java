package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.service.compass.grade.Grade;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

public interface CalculationEngine {

    Map.Entry<Long, Grade> getNextOptimalModel();

    Grade getResultForModel(long modelId);

    Collection<Long> getModelIds();

    /**
     * Add a new assessment
     *
     * @param assessment the new assessment as raw sting
     * @param modelId the id of the corresponding model
     */
    void notifyNewAssessment(String assessment, long modelId);

    /**
     * Add a new model
     *
     * @param model the new model as raw sting
     * @param modelId the id of the new model
     */
    void notifyNewModel(String model, long modelId);

    /**
     *
     * @return the time when the engine has been used last
     */
    LocalDateTime getLastUsedAt();

    Map<Long, Grade> getModelsWaitingForAssessment();

    void removeModelWaitingForAssessment(long modelId, boolean isAssessed);

    JsonObject exportToJson(Grade grade, long modelId);

}
