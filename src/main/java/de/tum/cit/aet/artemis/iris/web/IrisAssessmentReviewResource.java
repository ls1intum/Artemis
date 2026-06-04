package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentPointDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisQAExchangeDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;
import de.tum.cit.aet.artemis.iris.service.IrisAssessmentService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;

/**
 * REST controller for managing client requests from the iris-assessment review page.
 */
@Profile(PROFILE_IRIS)
@Lazy
@RestController
@RequestMapping("api/iris/assessment-review")
public class IrisAssessmentReviewResource {

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    private final AuthorizationCheckService authorizationCheckService;

    private final IrisAssessmentService irisAssessmentService;

    private final IrisAssessmentRepository irisAssessmentRepository;

    protected IrisAssessmentReviewResource(IrisExerciseChatSessionService irisExerciseChatSessionService, AuthorizationCheckService authorizationCheckService,
            IrisAssessmentService irisAssessmentService, IrisAssessmentRepository irisAssessmentRepository) {
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.authorizationCheckService = authorizationCheckService;
        this.irisAssessmentService = irisAssessmentService;
        this.irisAssessmentRepository = irisAssessmentRepository;
    }

    /**
     * GET assessment-review/{assessmentId}/chat: Retrieve the assessment chat
     *
     * @param assessmentId of the assessment
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the QAExchangeDTO objects for the assessment or {@code 404 (Not Found)} if no
     *         assessment exists
     */
    @GetMapping("{assessmentId}/chat")
    public ResponseEntity<List<IrisQAExchangeDTO>> getAssessmentChat(@PathVariable Long assessmentId) {
        var assessment = irisAssessmentRepository.findWithReasoningAndExerciseAndCourseByIdElseThrow(assessmentId);
        var user = assessment.getStudent();
        var exercise = validate(assessment.getExercise());

        return ResponseEntity.ok(irisExerciseChatSessionService.getQAExchangeDTOList(assessment, exercise, user));
    }

    /**
     * PATCH assessment-review/{assessmentId}/accept: Accepts the answers of the assessment (updates (old) verified score
     * depending on iris verdict and previous assessment)
     *
     * @param assessmentId of the assessment
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated assessment or {@code 404 (Not Found)} if no assessment exists
     */
    @PatchMapping("{assessmentId}/accept")
    public ResponseEntity<IrisAssessment> acceptAnswers(@PathVariable Long assessmentId) {
        var assessment = irisAssessmentRepository.findByIdElseThrow(assessmentId);
        validate(assessment.getExercise());
        var updatedAssessment = irisAssessmentService.acceptAnswers(assessment);
        reduceAssessmentFields(updatedAssessment);

        return ResponseEntity.ok(updatedAssessment);
    }

    /**
     * PATCH assessment-review/{assessmentId}/reject: Rejects the answers of the assessment (updates (old) verified score
     * depending on iris verdict and previous assessment)
     *
     * @param assessmentId of the assessment
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated assessment or {@code 404 (Not Found)} if no assessment exists
     */

    @PatchMapping("{assessmentId}/reject")
    public ResponseEntity<IrisAssessment> rejectAnswers(@PathVariable Long assessmentId) {
        var assessment = irisAssessmentRepository.findByIdElseThrow(assessmentId);
        validate(assessment.getExercise());
        var updatedAssessment = irisAssessmentService.rejectAnswers(assessment);
        reduceAssessmentFields(updatedAssessment);

        return ResponseEntity.ok(updatedAssessment);
    }

    /**
     * GET assessment-review/{assessmentId}: Gets the assessment for a given id
     * depending on iris verdict and previous assessment)
     *
     * @param assessmentId of the assessment
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the assessment or {@code 404 (Not Found)} if no assessment exists
     */

    @GetMapping("{assessmentId}")
    public ResponseEntity<IrisAssessmentPointDTO> findWithPoints(@PathVariable Long assessmentId) {
        var assessment = irisAssessmentRepository.findWithReasoningAndExerciseAndCourseByIdElseThrow(assessmentId);
        validate(assessment.getExercise());

        return ResponseEntity.ok(getIrisAssessmentPointDTO(assessment));
    }

    private Exercise validate(Exercise exercise) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        if (exercise.isTeamMode()) {
            throw new ConflictException("Prompting mode is not supported for team exercises", "Iris", "irisTeamExercise");
        }
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        authorizationCheckService.checkIsAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course.getId());

        return exercise;
    }

    private void reduceAssessmentFields(IrisAssessment assessment) {
        assessment.setReasoning(null);
        assessment.setVerdict(null);
        assessment.setExercise(null);
        assessment.setStudent(null);
    }

    private IrisAssessmentPointDTO getIrisAssessmentPointDTO(IrisAssessment assessment) {
        return new IrisAssessmentPointDTO(assessment.getId(), assessment.getStudent(), assessment.getExercise(), assessment.getVerdict(), assessment.getVerdictReview(),
                assessment.getVerifiedScore(), assessment.getVerifiedScoreOld(), assessment.getReasoning(), assessment.getLastEvent(),
                irisAssessmentService.getVerifiedPoints(assessment), irisAssessmentService.getVerifiedPointsOld(assessment));
    }
}
