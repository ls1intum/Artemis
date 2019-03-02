package de.tum.in.www1.artemis.service;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.compass.assessment.ModelElementAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static java.math.BigDecimal.ROUND_HALF_EVEN;

@Service
public class ModelingAssessmentService extends AssessmentService {
    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentService.class);

    private final ResultRepository resultRepository;
    private final UserService userService;
    private final ModelingSubmissionRepository modelingSubmissionRepository;
    private final ParticipationRepository participationRepository;

    public ModelingAssessmentService(ResultRepository resultRepository,
                                     UserService userService,
                                     ModelingSubmissionRepository modelingSubmissionRepository,
                                     ParticipationRepository participationRepository) {
        super(resultRepository);
        this.resultRepository = resultRepository;
        this.userService = userService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.participationRepository = participationRepository;
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
    public Result submitManualAssessment(Result result, ModelingExercise exercise, List<ModelElementAssessment> modelingAssessment) {
        Double calculatedScore = calculateTotalScore(modelingAssessment);
        return submitResult(result, exercise, calculatedScore);
    }


    /**
     * This function is used for manually assessed results. It updates the completion date, sets the
     * assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the assessment
     * in the file system the total score is calculated and set in the result.
     *
     * @param result             the result the assessment belongs to
     */
    @Transactional
    public void saveManualAssessment(Result result) {
        result.setAssessmentType(AssessmentType.MANUAL);
        User user = userService.getUser();
        result.setAssessor(user);

        if (result.getSubmission() instanceof ModelingSubmission && result.getSubmission().getResult() == null) {
            ModelingSubmission modelingSubmission = (ModelingSubmission) result.getSubmission();
            modelingSubmission.setResult(result);
            modelingSubmissionRepository.save(modelingSubmission);
        }
        resultRepository.save(result);
    }


    /**
     * @return sum of every modelingAssessments credit rounded to max two numbers after the comma
     */
    public static Double calculateTotalScore(List<ModelElementAssessment> modelingAssessment) {
        double totalScore = 0.0;
        for (ModelElementAssessment assessment : modelingAssessment) {
            totalScore += assessment.getCredits();
        }
        return new BigDecimal(totalScore).setScale(2, ROUND_HALF_EVEN).doubleValue();
    }
}
