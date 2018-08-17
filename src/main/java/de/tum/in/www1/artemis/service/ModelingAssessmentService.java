package de.tum.in.www1.artemis.service;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.repository.JsonAssessmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ModelingAssessmentService {
    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentService.class);

    private final JsonAssessmentRepository jsonAssessmentRepository;

    public ModelingAssessmentService(JsonAssessmentRepository jsonAssessmentRepository) {
        this.jsonAssessmentRepository = jsonAssessmentRepository;
    }

    /**
     * Find latest assessment for given exerciseId, studentId and modelId. First checks for existence of manual assessment,
     * then of automatic assessment.
     *
     * @param exerciseId
     * @param studentId
     * @param modelId
     * @return
     */
    public String findLatestAssessment(Long exerciseId, Long studentId, Long modelId) {
        JsonObject assessmentJson = null;
        if (jsonAssessmentRepository.exists(exerciseId, studentId, modelId, true)) {
            // the modelingSubmission was graded manually
            assessmentJson = jsonAssessmentRepository.readAssessment(exerciseId, studentId, modelId, true);
        } else if (jsonAssessmentRepository.exists(exerciseId, studentId, modelId, false)) {
            // the modelingSubmission was graded automatically
            assessmentJson = jsonAssessmentRepository.readAssessment(exerciseId, studentId, modelId, false);
        }

        if (assessmentJson != null) {
            return assessmentJson.get("assessments").toString();
        }
        return null;
    }
}
