package de.tum.cit.aet.artemis.iris.web;

import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.web.util.PaginationUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentReviewPageDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentReviewSearchDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisQAExchangeDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisQuizTimerDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;
import de.tum.cit.aet.artemis.iris.service.IrisAssessmentReviewService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for managing client requests from the iris-assessment review page.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisAssessmentReviewResource {

    private static final Logger log = LoggerFactory.getLogger(IrisAssessmentReviewResource.class);

    private final AuthorizationCheckService authorizationCheckService;

    private final IrisAssessmentReviewService irisAssessmentReviewService;

    private final IrisAssessmentRepository irisAssessmentRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final IrisSettingsService irisSettingsService;

    protected IrisAssessmentReviewResource(AuthorizationCheckService authorizationCheckService, IrisAssessmentReviewService irisAssessmentReviewService,
            IrisAssessmentRepository irisAssessmentRepository, ProgrammingExerciseRepository programmingExerciseRepository, IrisSettingsService irisSettingsService) {
        this.authorizationCheckService = authorizationCheckService;
        this.irisAssessmentReviewService = irisAssessmentReviewService;
        this.irisAssessmentRepository = irisAssessmentRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.irisSettingsService = irisSettingsService;
    }

    /**
     * GET assessments/{assessmentId}/chat: Retrieve the assessment chat
     *
     * @param assessmentId of the assessment
     * @param inClass      whether the wanted chat is part of an in-class quiz session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the QAExchangeDTO objects for the assessment or {@code 404 (Not Found)} if no
     *         assessment exists
     */
    @GetMapping("assessments/{assessmentId}/chat")
    @EnforceAtLeastTutor
    public ResponseEntity<List<IrisQAExchangeDTO>> getAssessmentChat(@PathVariable Long assessmentId, @RequestParam(defaultValue = "false") boolean inClass) {
        var assessment = irisAssessmentRepository.findWithReasoningAndExerciseAndStudentByIdElseThrow(assessmentId);
        var user = assessment.getStudent();
        var exercise = validate(assessment.getExercise());

        return ResponseEntity.ok(irisAssessmentReviewService.getQAExchangeDTOList(assessment, exercise, user, inClass));
    }

    /**
     * PATCH assessments/{assessmentId}/accept: Accepts the answers of the assessment.
     *
     * @param assessmentId of the assessment
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} or {@code 404 (Not Found)} if no assessment exists
     */
    @PatchMapping("assessments/{assessmentId}/accept")
    @EnforceAtLeastEditor
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
    @EnforceAtLeastEditor
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
    @EnforceAtLeastTutor
    public ResponseEntity<IrisAssessmentDTO> findWithStudent(@PathVariable Long assessmentId) {
        var assessment = irisAssessmentRepository.findWithStudentByIdElseThrow(assessmentId);
        validate(assessment.getExercise());

        return ResponseEntity.ok(IrisAssessmentDTO.of(assessment));
    }

    /**
     * GET courses/{courseId}/assessment-review/participations : get paginated Iris assessment review participations for all programming exercises in a course.
     *
     * @param courseId The courseId of the course
     * @param search   search parameters including pagination, search term, and verdict filters
     * @param inClass  Whether the request is for the Iris in-class assessment overview
     * @return paged participations with pagination headers and filter counts
     */
    @GetMapping("courses/{courseId}/assessment-review/participations")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<IrisAssessmentReviewPageDTO> getAssessmentReviewParticipationsForCourse(@PathVariable long courseId, @Valid IrisAssessmentReviewSearchDTO search,
            @RequestParam(defaultValue = "false") boolean inClass) {
        log.debug("REST request to search Iris assessment review Participations for Course {}, inClass: {}", courseId, inClass);

        var searchResult = irisAssessmentReviewService.findAssessmentReviewParticipationsForCourse(courseId, search, inClass);
        Page<IrisAssessmentProgrammingStudentParticipationDTO> page = searchResult.page();

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(new IrisAssessmentReviewPageDTO(page.getContent(), searchResult.participationsPerFilter()));
    }

    /**
     * PATCH programming-exercises/{exerciseId}/ask-user/in-class/available: Starts the instructor-controlled in-class quiz window.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the corresponding timer data
     */
    @PatchMapping("programming-exercises/{exerciseId}/ask-user/in-class/available")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<IrisQuizTimerDTO> makeInClassQuizAvailable(@PathVariable long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);

        return ResponseEntity.ok(irisAssessmentReviewService.makeInClassQuizAvailable(exercise));
    }

    /**
     * GET programming-exercises/{exerciseId}/ask-user/in-class: Gets the active editor-controlled in-class quiz window.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the timer data, or {@code null} if no timer is active
     */
    @GetMapping("programming-exercises/{exerciseId}/ask-user/in-class")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<IrisQuizTimerDTO> getAvailableInClassQuiz(@PathVariable long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);

        return ResponseEntity.ok(irisAssessmentReviewService.getAvailableInClassQuiz(exercise));
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
