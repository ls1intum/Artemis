package de.tum.in.www1.artemis.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.ModelingExercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.JsonAssessmentRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.time.ZonedDateTime;

@Service
public class ModelingAssessmentService {
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
        this.jsonAssessmentRepository = jsonAssessmentRepository;
        this.resultRepository = resultRepository;
        this.userService = userService;
        this.modelingExerciseService = modelingExerciseService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
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

    /**
     * This function is used for manually graded results. It updates the completion date, sets the assessment type to MANUAL
     * and sets the assessor attribute. Furthermore, it saves the assessment in the file system and if the result is rated,
     * i.e. the assessment was submitted, the total score is calculated and set in the result.
     *
     * @param resultId              the resultId the assessment belongs to
     * @param exerciseId            the exerciseId the assessment belongs to
     * @param modelingAssessment    the assessments as string
     * @param rated                 if the result is rated or not (false if only save assessment, true if submit assessment)
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result updateManualResult(Long resultId, Long exerciseId, String modelingAssessment, Boolean rated) {
        Result result = resultRepository.findById(resultId).get();
        result.setRated(rated);
        result.setCompletionDate(ZonedDateTime.now());
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

        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);

        // write assessment to file system
        jsonAssessmentRepository.writeAssessment(exerciseId, studentId, submissionId, true, modelingAssessment);

        if (rated) {
            // set score, result string and successful if rated
            JsonObject assessmentJson = jsonAssessmentRepository.readAssessment(exerciseId, studentId, submissionId, true);
            Double maxScore = modelingExercise.getMaxScore();
            Double totalScore = Math.min(Math.max(0, calculateTotalScore(assessmentJson)), maxScore);
            Double percentageScore = totalScore/maxScore*100;
            result.setScore(Math.round(percentageScore));
            DecimalFormat formatter = new DecimalFormat("#.##"); // limit decimal places to 2
            result.setResultString(formatter.format(totalScore) + " of " + formatter.format(modelingExercise.getMaxScore()) + " points");
            result.setSuccessful(result.getScore() == 100L);
        }

        resultRepository.save(result);

        return result;
    }

    /**
     * Helper function to calculate the total score of an assessment json. It loops through all assessed model elements
     * and sums the credits up.
     *
     * @param assessmentJson    the assessments as JsonObject
     * @return the total score
     */
    public Double calculateTotalScore(JsonObject assessmentJson) {
        Double totalScore = 0.0;
        JsonArray assessments = assessmentJson.get("assessments").getAsJsonArray();
        for (JsonElement assessment : assessments) {
            totalScore += assessment.getAsJsonObject().getAsJsonPrimitive("credits").getAsDouble();
        }
        return totalScore;
    }
}
