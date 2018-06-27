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

    void notifyNewAssessment(String assessment, long modelId);

    void notifyNewModel(String model, long modelId);

    LocalDateTime getLastUsedAt();

    Map<Long, Grade> getModelsWaitingForAssessment();

    void removeModelWaitingForAssessment(long modelId, boolean isAssessed);

    JsonObject exportToJson(Grade grade, long modelId);

}
