package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.ModelingExercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
public class ModelingAssessmentService extends AssessmentService {
    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentService.class);

    private final UserService userService;
    private final ModelingSubmissionRepository modelingSubmissionRepository;

    public ModelingAssessmentService(ResultRepository resultRepository,
                                     UserService userService,
                                     ModelingSubmissionRepository modelingSubmissionRepository) {
        super(resultRepository);
        this.userService = userService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
    }


    /**
     * This function is used for manually assessed results. It updates the completion date, sets the
     * assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the assessment
     * in the file system the total score is calculated and set in the result.
     *
     * @param result   the result the assessment belongs to
     * @param exercise the exercise the assessment belongs to
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result submitManualAssessment(Result result, ModelingExercise exercise) {
        result.setRatedIfNotExceeded(exercise.getDueDate(), result.getSubmission().getSubmissionDate());
        result.setCompletionDate(ZonedDateTime.now());
        result.evaluateFeedback(exercise.getMaxScore());
        resultRepository.save(result);
        return result;
    }


    /**
     * This function is used for manually assessed results. It updates the completion date, sets the
     * assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the assessment
     * in the file system the total score is calculated and set in the result.
     *
     * @param result the result the assessment belongs to
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


}
