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
import de.tum.cit.aet.artemis.iris.dto.IrisQAExchangeDTO;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;

/**
 * REST controller for managing client requests from the iris-assessment review page.
 */
@Profile(PROFILE_IRIS)
@Lazy
@RestController
@RequestMapping("api/iris/assessment-review")
public class IrisAssessmentReviewResource {

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ProgrammingExerciseParticipationService participationService;

    protected IrisAssessmentReviewResource(IrisExerciseChatSessionService irisExerciseChatSessionService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, AuthorizationCheckService authorizationCheckService,
            ProgrammingExerciseParticipationService participationService) {
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.participationService = participationService;
    }

    /**
     * GET assessment-review/{participationId}: Retrieve the assessment chat of a participation
     *
     * @param participationId of the participation
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the QAExchangeDTO objects for the participation or {@code 404 (Not Found)} if no
     *         assessment exists
     */
    @GetMapping("{participationId}")
    public ResponseEntity<List<IrisQAExchangeDTO>> getAssessmentChat(@PathVariable Long participationId) {
        var participation = programmingExerciseStudentParticipationRepository.findWithIrisReasoningByIdElseThrow(participationId);
        var user = participation.getStudent().orElseThrow();
        var exercise = validate(participation.getExercise());

        return ResponseEntity.ok(irisExerciseChatSessionService.getQAExchangeDTOList(participation, exercise, user));
    }

    /**
     * PATCH assessment-review/{participationId}/accept: Accepts the answers of the assessment belonging to a participation (updates (old) verified score
     * depending on iris verdict and previous assessment)
     *
     * @param participationId of the participation
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated participation or {@code 404 (Not Found)} if no participation exists
     */
    @PatchMapping("{participationId}/accept")
    public ResponseEntity<ProgrammingExerciseStudentParticipation> acceptAnswers(@PathVariable Long participationId) {
        var participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        validate(participation.getExercise());

        return ResponseEntity.ok(participationService.acceptAnswers(participation));
    }

    /**
     * PATCH assessment-review/{participationId}/reject: Rejects the answers of the assessment belonging to a participation (updates (old) verified score
     * depending on iris verdict and previous assessment)
     *
     * @param participationId of the participation
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated participation or {@code 404 (Not Found)} if no participation exists
     */

    @PatchMapping("{participationId}/reject")
    public ResponseEntity<ProgrammingExerciseStudentParticipation> rejectAnswers(@PathVariable Long participationId) {
        var participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        validate(participation.getExercise());

        return ResponseEntity.ok(participationService.rejectAnswers(participation));
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
}
