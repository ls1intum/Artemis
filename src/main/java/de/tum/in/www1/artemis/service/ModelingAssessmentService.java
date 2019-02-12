package de.tum.in.www1.artemis.service;

import java.math.BigDecimal;
import java.util.List;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.compass.assessment.ModelElementAssessment;
import static java.math.BigDecimal.ROUND_HALF_EVEN;

@Service
public class ModelingAssessmentService extends AssessmentService {
    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentService.class);

    private final JsonAssessmentRepository jsonAssessmentRepository;
    private final ResultRepository resultRepository;
    private final UserService userService;
    private final ModelingExerciseService modelingExerciseService;
    private final ModelingSubmissionRepository modelingSubmissionRepository;


    public ModelingAssessmentService(JsonAssessmentRepository jsonAssessmentRepository,
                                     ResultRepository resultRepository,
                                     UserService userService,
                                     ModelingExerciseService modelingExerciseService,
                                     ModelingSubmissionRepository modelingSubmissionRepository) {
        super(resultRepository);

        this.jsonAssessmentRepository = jsonAssessmentRepository;
        this.resultRepository = resultRepository;
        this.userService = userService;
        this.modelingExerciseService = modelingExerciseService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
    }


    /**
     * Find latest assessment for given exerciseId, studentId and modelId. First checks for existence of manual
     * assessment, then of automatic assessment.
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


    /**
     * This function is used for manually assessed results. It updates the completion date, sets the
     * assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the assessment
     * in the file system the total score is calculated and set in the result.
     *
     * @param result             the result the assessment belongs to
     * @param exercise           the exercise the assessment belongs to
     * @param modelingAssessment the assessments as string
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result submitManualAssessment(
        Result result, ModelingExercise exercise, List<ModelElementAssessment> modelingAssessment) {
        saveManualAssessment(result, exercise.getId(), modelingAssessment);
        Double calculatedScore = calculateTotalScore(modelingAssessment);
        return prepareSubmission(result, exercise, calculatedScore);
    }


    /**
     * This function is used for manually assessed results. It updates the completion date, sets the
     * assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the assessment
     * in the file system the total score is calculated and set in the result.
     *
     * @param result             the result the assessment belongs to
     * @param exerciseId         the exerciseId the assessment belongs to
     * @param modelingAssessment List of assessed model elements
     */
    @Transactional
    public void saveManualAssessment(
        Result result, Long exerciseId, List<ModelElementAssessment> modelingAssessment) {
        result.setAssessmentType(AssessmentType.MANUAL);
        User user = userService.getUser();
        result.setAssessor(user);

        Long studentId = result.getParticipation().getStudent().getId();
        Long submissionId = result.getSubmission().getId();

        if (result.getSubmission() instanceof ModelingSubmission && result.getSubmission().getResult() == null) {
            ModelingSubmission modelingSubmission = (ModelingSubmission) result.getSubmission();
            modelingSubmission.setResult(result);
            modelingSubmissionRepository.save(modelingSubmission);
        }
        // write assessment to file system
        jsonAssessmentRepository.writeAssessment(exerciseId, studentId, submissionId, true, modelingAssessment);
        resultRepository.save(result);
    }


    public static Double calculateTotalScore(List<ModelElementAssessment> modelingAssessment) {
        double totalScore = 0.0;
        for (ModelElementAssessment assessment : modelingAssessment) {
            totalScore += assessment.getCredits();
        }
        return new BigDecimal(totalScore).setScale(2, ROUND_HALF_EVEN).doubleValue();
    }
}
