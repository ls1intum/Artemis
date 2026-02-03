package de.tum.cit.aet.artemis.modeling.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateDTO;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.assessment.web.AssessmentResource;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.dto.ModelingAssessmentDTO;
import de.tum.cit.aet.artemis.modeling.dto.ResultDTO;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;

/**
 * REST controller for managing ModelingAssessment.
 */
@Conditional(ModelingEnabled.class)
@Lazy
@RestController
@RequestMapping("api/modeling/")
public class ModelingAssessmentResource extends AssessmentResource {

    private static final Logger log = LoggerFactory.getLogger(ModelingAssessmentResource.class);

    private static final String ENTITY_NAME = "modelingAssessment";

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    public ModelingAssessmentResource(AuthorizationCheckService authCheckService, UserRepository userRepository, ModelingExerciseRepository modelingExerciseRepository,
            AssessmentService assessmentService, ModelingSubmissionRepository modelingSubmissionRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            ExerciseRepository exerciseRepository, ResultRepository resultRepository, SubmissionRepository submissionRepository) {
        super(authCheckService, userRepository, exerciseRepository, assessmentService, resultRepository, exampleSubmissionRepository, submissionRepository);
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.authCheckService = authCheckService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
    }

    /**
     * Get the result of the modeling submission with the given id as DTO.
     * For Athena results, a simplified authorization check is performed since Athena results don't have an assessor.
     *
     * @param submissionId the id of the submission that should be sent to the client
     * @return the assessment of the given submission as DTO
     */
    @GetMapping("modeling-submissions/{submissionId}/result")
    @EnforceAtLeastStudent
    public ResponseEntity<ResultDTO> getModelingAssessmentBySubmissionId(@PathVariable Long submissionId) {
        log.debug("REST request to get modeling assessment for submission with id {}", submissionId);
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNoteAndTeamStudents(submissionId);
        if (submission == null) {
            throw new EntityNotFoundException("Submission", submissionId);
        }
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();
        Exercise exercise = participation.getExercise();

        Result result = submission.getLatestResult();
        if (result == null) {
            throw new EntityNotFoundException("Result with submission", submissionId);
        }

        // For Athena results, use special authorization logic (more permissive, no date restrictions)
        if (result.getAssessmentType() == AssessmentType.AUTOMATIC_ATHENA) {
            if (!authCheckService.isAtLeastStudentForExercise(exercise)
                    || (!authCheckService.isOwnerOfParticipation(participation) && !authCheckService.isAtLeastTeachingAssistantForExercise(exercise))) {
                throw new AccessForbiddenException();
            }
        }
        else {
            // For regular results, apply standard access control (includes due date checks)
            if (!authCheckService.isUserAllowedToGetResult(exercise, participation, result)) {
                throw new AccessForbiddenException();
            }
        }

        // remove sensitive information for students
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            exercise.filterSensitiveInformation();
            result.filterSensitiveInformation();
        }

        return ResponseEntity.ok(ResultDTO.of(result));
    }

    /**
     * Retrieve the result for an example submission, only if the user is an instructor or if the example submission is not used for tutorial purposes.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the example submission
     * @return the result linked to the example submission
     */
    @GetMapping("exercise/{exerciseId}/modeling-submissions/{submissionId}/example-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> getModelingExampleAssessment(@PathVariable long exerciseId, @PathVariable long submissionId) {
        log.debug("REST request to get example assessment for tutors text assessment: {}", submissionId);
        return super.getExampleAssessment(exerciseId, submissionId);
    }

    /**
     * PUT modeling-submissions/:submissionId/result/resultId/assessment : save manual modeling assessment. See {@link AssessmentResource#saveAssessment}.
     *
     * @param submissionId       id of the submission
     * @param resultId           id of the result
     * @param submit             if true the assessment is submitted, else only saved
     * @param modelingAssessment the DTO containing the list of feedbacks and the assessment note, if one exists
     * @return result after saving/submitting modeling assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("modeling-submissions/{submissionId}/result/{resultId}/assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> saveModelingAssessment(@PathVariable long submissionId, @PathVariable long resultId,
            @RequestParam(value = "submit", defaultValue = "false") boolean submit, @RequestBody ModelingAssessmentDTO modelingAssessment) {
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submissionId);
        return super.saveAssessment(submission, submit, modelingAssessment.feedbacks(), resultId, modelingAssessment.assessmentNote());
    }

    /**
     * PUT modeling-submissions/:submissionId/example-assessment : save manual example modeling assessment
     *
     * @param exampleSubmissionId id of the example submission
     * @param feedbacks           list of feedbacks
     * @return result after saving example modeling assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("modeling-submissions/{exampleSubmissionId}/example-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> saveModelingExampleAssessment(@PathVariable long exampleSubmissionId, @RequestBody List<Feedback> feedbacks) {
        log.debug("REST request to save modeling example assessment : {}", exampleSubmissionId);
        Result result = saveExampleAssessment(exampleSubmissionId, feedbacks);
        return ResponseEntity.ok(result);
    }

    /**
     * Update an assessment after a complaint was accepted.
     *
     * @param submissionId     the id of the submission for which the assessment should be updated
     * @param assessmentUpdate the assessment update containing the new feedback items and the response to the complaint
     * @return the updated result
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("modeling-submissions/{submissionId}/assessment-after-complaint")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> updateModelingAssessmentAfterComplaint(@PathVariable Long submissionId, @RequestBody AssessmentUpdateDTO assessmentUpdate) {
        log.debug("REST request to update the assessment of submission {} after complaint.", submissionId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        ModelingSubmission modelingSubmission = modelingSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(submissionId);
        long exerciseId = modelingSubmission.getParticipation().getExercise().getId();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);
        checkAuthorization(modelingExercise, user);

        Result result = assessmentService.updateAssessmentAfterComplaint(modelingSubmission.getLatestResult(), modelingExercise, assessmentUpdate);

        var participation = result.getSubmission().getParticipation();
        // remove circular dependencies if the results of the participation are there

        if (participation instanceof StudentParticipation studentParticipation && !authCheckService.isAtLeastInstructorForExercise(modelingExercise, user)) {
            studentParticipation.setParticipant(null);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submissionId the id of the submission for which the current assessment should be canceled
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not the assessor of the submission
     */
    @PutMapping("modeling-submissions/{submissionId}/cancel-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> cancelAssessment(@PathVariable Long submissionId) {
        return super.cancelAssessment(submissionId);
    }

    /**
     * Delete an assessment of a given submission.
     *
     * @param participationId - the id of the participation to the submission
     * @param submissionId    - the id of the submission for which the current assessment should be deleted
     * @param resultId        - the id of the result which should get deleted
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not an instructor of the course or an admin
     */
    @Override
    @DeleteMapping("participations/{participationId}/modeling-submissions/{submissionId}/results/{resultId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long participationId, @PathVariable Long submissionId, @PathVariable Long resultId) {
        return super.deleteAssessment(participationId, submissionId, resultId);
    }

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }
}
