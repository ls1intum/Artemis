package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
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
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisQAExchangeDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;
import de.tum.cit.aet.artemis.iris.service.IrisAssessmentService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * REST controller for managing client requests from the iris-assessment review page.
 */
@Profile(PROFILE_IRIS)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisAssessmentReviewResource {

    private static final Logger log = LoggerFactory.getLogger(IrisAssessmentReviewResource.class);

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    private final AuthorizationCheckService authorizationCheckService;

    private final IrisAssessmentService irisAssessmentService;

    private final IrisAssessmentRepository irisAssessmentRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    protected IrisAssessmentReviewResource(IrisExerciseChatSessionService irisExerciseChatSessionService, AuthorizationCheckService authorizationCheckService,
            IrisAssessmentService irisAssessmentService, IrisAssessmentRepository irisAssessmentRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, StudentParticipationRepository studentParticipationRepository) {
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.authorizationCheckService = authorizationCheckService;
        this.irisAssessmentService = irisAssessmentService;
        this.irisAssessmentRepository = irisAssessmentRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.studentParticipationRepository = studentParticipationRepository;
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
    public ResponseEntity<List<IrisQAExchangeDTO>> getAssessmentChat(@PathVariable Long assessmentId) {
        var assessment = irisAssessmentRepository.findWithReasoningAndExerciseAndCourseByIdElseThrow(assessmentId);
        var user = assessment.getStudent();
        var exercise = validate(assessment.getExercise());

        return ResponseEntity.ok(irisExerciseChatSessionService.getQAExchangeDTOList(assessment, exercise, user));
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
        var assessment = irisAssessmentRepository.findByIdElseThrow(assessmentId);
        validate(assessment.getExercise());
        irisAssessmentService.acceptAnswers(assessment);

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
        var assessment = irisAssessmentRepository.findByIdElseThrow(assessmentId);
        validate(assessment.getExercise());
        irisAssessmentService.rejectAnswers(assessment);

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
        return ResponseEntity.ok(getAllParticipationsNonZeroLatestScoreForExerciseId(exerciseId, inClass));
    }

    private Set<IrisAssessmentProgrammingStudentParticipationDTO> getAllParticipationsNonZeroLatestScoreForExerciseId(long exerciseId, boolean inClass) {
        Exercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var participations = inClass
                ? programmingExerciseStudentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerStudentAndEagerAssessmentInClassByExerciseId(exercise.getId())
                : programmingExerciseStudentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerStudentAndEagerAssessmentByExerciseId(exercise.getId());

        var filteredParticipations = participations.stream().filter(p -> p.findLatestResult() != null && p.findLatestResult().getScore() > 0).collect(Collectors.toSet());

        Map<Long, Integer> submissionCountMap = studentParticipationRepository.countSubmissionsPerParticipationByExerciseIdAsMap(exerciseId);
        filteredParticipations.forEach(participation -> participation.setSubmissionCount(submissionCountMap.get(participation.getId())));
        Set<IrisAssessmentProgrammingStudentParticipationDTO> participationDTOs = filteredParticipations.stream().filter(participation -> participation.getParticipant() != null)
                .map(p -> IrisAssessmentProgrammingStudentParticipationDTO.of(p, inClass)).collect(Collectors.toSet());

        return participationDTOs;
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
