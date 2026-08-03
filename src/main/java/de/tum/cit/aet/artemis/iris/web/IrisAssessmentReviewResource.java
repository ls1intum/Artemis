package de.tum.cit.aet.artemis.iris.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisQAExchangeDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisQuizTimerDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;
import de.tum.cit.aet.artemis.iris.service.IrisAssessmentReviewService;
import de.tum.cit.aet.artemis.iris.service.session.IrisAskUserService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * REST controller for managing client requests from the iris-assessment review page.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisAssessmentReviewResource {

    private static final Logger log = LoggerFactory.getLogger(IrisAssessmentReviewResource.class);

    private final IrisAskUserService irisAskUserService;

    private final AuthorizationCheckService authorizationCheckService;

    private final IrisAssessmentReviewService irisAssessmentReviewService;

    private final IrisAssessmentRepository irisAssessmentRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final IrisSettingsService irisSettingsService;

    protected IrisAssessmentReviewResource(IrisAskUserService irisAskUserService, AuthorizationCheckService authorizationCheckService,
            IrisAssessmentReviewService irisAssessmentReviewService, IrisAssessmentRepository irisAssessmentRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, StudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, IrisSettingsService irisSettingsService) {
        this.irisAskUserService = irisAskUserService;
        this.authorizationCheckService = authorizationCheckService;
        this.irisAssessmentReviewService = irisAssessmentReviewService;
        this.irisAssessmentRepository = irisAssessmentRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.irisSettingsService = irisSettingsService;
    }

    /**
     * GET assessments/{assessmentId}/chat: Retrieve the assessment chat
     *
     * @param assessmentId of the assessment
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the QAExchangeDTO objects for the assessment or {@code 404 (Not Found)} if no
     *         assessment exists
     */
    @GetMapping("assessments/{assessmentId}/chat")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<IrisQAExchangeDTO>> getAssessmentChat(@PathVariable Long assessmentId, @RequestParam(defaultValue = "false") boolean inClass) {
        var assessment = irisAssessmentRepository.findWithReasoningAndExerciseAndCourseByIdElseThrow(assessmentId);
        var user = assessment.getStudent();
        var exercise = validate(assessment.getExercise());

        return ResponseEntity.ok(irisAskUserService.getQAExchangeDTOList(assessment, exercise, user, inClass));
    }

    /**
     * PATCH assessments/{assessmentId}/accept: Accepts the answers of the assessment.
     *
     * @param assessmentId of the assessment
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} or {@code 404 (Not Found)} if no assessment exists
     */
    @PatchMapping("assessments/{assessmentId}/accept")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> acceptAnswers(@PathVariable Long assessmentId) {
        var assessment = irisAssessmentRepository.findWithExerciseAndCourseByIdElseThrow(assessmentId);
        validate(assessment.getExercise());
        irisAssessmentReviewService.acceptAnswers(assessment);

        return ResponseEntity.ok().build();
    }

    /**
     * PATCH assessments/{assessmentId}/reject: Rejects the answers of the assessment.
     *
     * @param assessmentId of the assessment
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} or {@code 404 (Not Found)} if no assessment exists
     */

    @PatchMapping("assessments/{assessmentId}/reject")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> rejectAnswers(@PathVariable Long assessmentId) {
        var assessment = irisAssessmentRepository.findWithExerciseAndCourseByIdElseThrow(assessmentId);
        validate(assessment.getExercise());
        irisAssessmentReviewService.rejectAnswers(assessment);

        return ResponseEntity.ok().build();
    }

    /**
     * GET assessments/{assessmentId}: Gets the assessment for a given id
     * depending on iris verdict and previous assessment
     *
     * @param assessmentId of the assessment
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the assessment or {@code 404 (Not Found)} if no assessment exists
     */

    @GetMapping("assessments/{assessmentId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<IrisAssessmentDTO> findWithPoints(@PathVariable Long assessmentId) {
        var assessment = irisAssessmentRepository.findWithReasoningAndExerciseAndCourseByIdElseThrow(assessmentId);
        validate(assessment.getExercise());

        return ResponseEntity.ok(IrisAssessmentDTO.of(assessment));
    }

    /**
     * GET programming-exercises/{exerciseId}/participations/non-zero-latest-score : get all the participations for a programming exercise if latest score > 0 was achieved
     *
     * @param exerciseId The exerciseId of the programming exercise
     * @param inClass    Whether the request is for the Iris in-class assessment overview
     * @return A list of all programming student participations for the exercise
     */
    @GetMapping("programming-exercises/{exerciseId}/participations/non-zero-latest-score")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<Set<IrisAssessmentProgrammingStudentParticipationDTO>> getAllParticipationsNonZeroLatestScoreForExercise(@PathVariable Long exerciseId,
            @RequestParam(defaultValue = "false") boolean inClass) {
        log.info("REST request to get all Participations with non-zero highest score for Exercise {} for Iris assessment overview, inClass: {}", exerciseId, inClass);
        return ResponseEntity.ok(getAllNonPracticeParticipationsNonZeroLatestScoreForExerciseId(exerciseId, inClass));
    }

    private Set<IrisAssessmentProgrammingStudentParticipationDTO> getAllNonPracticeParticipationsNonZeroLatestScoreForExerciseId(long exerciseId, boolean inClass) {
        var participationProjections = (inClass
                ? programmingExerciseStudentParticipationRepository
                        .findAllNonPracticeIrisAssessmentInClassParticipationProjectionsByExerciseIdAndLatestResultScoreGreaterThanZero(exerciseId)
                : programmingExerciseStudentParticipationRepository
                        .findAllNonPracticeIrisAssessmentParticipationProjectionsByExerciseIdAndLatestResultScoreGreaterThanZero(exerciseId));

        Map<Long, Integer> submissionCountMap = studentParticipationRepository
                .countSubmissionsPerParticipationByIdsAsMap(participationProjections.stream().map(projection -> projection.id()).toList());
        Set<IrisAssessmentProgrammingStudentParticipationDTO> participationDTOs = participationProjections.stream()
                .map(projection -> projection.toDto(submissionCountMap.get(projection.id()))).collect(Collectors.toSet());

        return participationDTOs;
    }

    /**
     * PATCH programming-exercises/{exerciseId}/ask-user/in-class/start: Starts the instructor-controlled in-class quiz window.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the corresponding timer data
     */
    @PatchMapping("programming-exercises/{exerciseId}/ask-user/in-class/available")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<IrisQuizTimerDTO> makeInClassQuizAvailable(@PathVariable Long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);

        return ResponseEntity.ok(irisAskUserService.makeInClassQuizAvailable(exercise));
    }

    /**
     * GET programming-exercises/{exerciseId}/ask-user/in-class: Gets the active instructor-controlled in-class quiz window.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the timer data, or {@code null} if no timer is active
     */
    @GetMapping("programming-exercises/{exerciseId}/ask-user/in-class")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<IrisQuizTimerDTO> getAvailableInClassQuiz(@PathVariable Long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);

        return ResponseEntity.ok(irisAskUserService.getAvailableInClassQuiz(exercise));
    }

    private Exercise validate(Exercise exercise) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        if (exercise.isTeamMode()) {
            throw new ConflictException("Ask-user mode is not supported for team exercises", "Iris", "irisTeamExercise");
        }
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        authorizationCheckService.checkIsAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course.getId());

        return exercise;
    }

}
