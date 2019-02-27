package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.service.compass.assessment.ModelElementAssessment;
import de.tum.in.www1.artemis.service.compass.grade.Grade;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface CalculationEngine {

    Map.Entry<Long, Grade> getNextOptimalModel();

    Grade getResultForModel(long modelId);

    Collection<Long> getModelIds();

    /**
     * Add a new assessment
     *
     * @param modelingAssessment the new assessment as list of individual model element assessments
     * @param submissionId       the id of the corresponding model
     */
    void notifyNewAssessment(List<ModelElementAssessment> modelingAssessment, long submissionId);

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
