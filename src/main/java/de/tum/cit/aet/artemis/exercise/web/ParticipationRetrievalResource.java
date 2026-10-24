package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.web.util.PaginationUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.DetailedParticipationDTO;
import de.tum.cit.aet.artemis.exercise.dto.DetailedSubmissionDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationManagementDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationNameExportDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationSearchDTO;
import de.tum.cit.aet.artemis.exercise.dto.StudentParticipationDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;

/**
 * REST controller for retrieving information about participations.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.EXERCISE_PARTICIPATIONS_STAFF)
@RestController
@RequestMapping("api/exercise/")
public class ParticipationRetrievalResource {

    private static final Logger log = LoggerFactory.getLogger(ParticipationRetrievalResource.class);

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final SubmissionRepository submissionRepository;

    public ParticipationRetrievalResource(ParticipationService participationService, ExerciseRepository exerciseRepository, AuthorizationCheckService authCheckService,
            ParticipationAuthorizationCheckService participationAuthCheckService, UserRepository userRepository, StudentParticipationRepository studentParticipationRepository,
            SubmissionRepository submissionRepository) {
        this.participationService = participationService;
        this.exerciseRepository = exerciseRepository;
        this.authCheckService = authCheckService;
        this.participationAuthCheckService = participationAuthCheckService;
        this.userRepository = userRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * GET /participations/:participationId : get the participation for the given "participationId" including its latest result.
     *
     * @param participationId the participationId of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @FeatureUsage(UserFeature.EXERCISE_PARTICIPATION)
    @GetMapping("participations/{participationId}/with-latest-result")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentParticipationDTO> getParticipationWithLatestResult(@PathVariable Long participationId) {
        log.debug("REST request to get Participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithResultsElseThrow(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);

        // Map only the results the owning student may already see, so the score of a not-yet-released manual assessment does not leak here as it does not through the dedicated
        // student endpoints. The participation itself is always returned; the hidden results are filtered out while mapping, never by mutating the loaded submissions (their result
        // collection uses orphan removal, so dropping an entry could delete the result).
        var response = StudentParticipationDTO.ofWithLatestResult(participation, visibleResultIds(participation));
        return ResponseEntity.ok(canSeeParticipant(participation) ? response : response.withoutParticipantInformation());
    }

    /**
     * GET /participations/:participationId : get the participation for the given "participationId".
     *
     * @param participationId the participationId of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @FeatureUsage(UserFeature.EXERCISE_PARTICIPATION)
    @GetMapping("participations/{participationId}")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentParticipationDTO> getParticipationForCurrentUser(@PathVariable Long participationId) {
        log.debug("REST request to get participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithEagerTeamStudentsElseThrow(participationId);
        User user = userRepository.getUserWithAuthorities();
        checkAccessPermissionOwner(participation, user);
        var response = StudentParticipationDTO.ofForCurrentUser(participation);
        return ResponseEntity.ok(canSeeParticipant(participation) ? response : response.withoutParticipantInformation());
    }

    /**
     * Determines the ids of the results the owning student may already see for the given participation. For an exam exercise no result is shown until the student may see it. For a
     * course exercise a manual assessment only becomes visible once it is completed and the assessment due date has passed, while automatic results are always visible. This
     * mirrors
     * the gate the dedicated programming retrieval endpoints apply and is owner-based, like them. The participation is only read here, never mutated, because its submissions'
     * result
     * collection uses orphan removal.
     *
     * @param participation the participation whose results are inspected
     * @return the ids of the results that may be returned to the student
     */
    private Set<Long> visibleResultIds(StudentParticipation participation) {
        if (participation.getSubmissions() == null || participationAuthCheckService.shouldHideExamExerciseResults(participation)) {
            return Set.of();
        }
        Exercise exercise = participation.getExercise();
        boolean manualResultsReleased = exercise.getAssessmentDueDate() == null || exercise.getAssessmentDueDate().isBefore(ZonedDateTime.now());
        return participation.getSubmissions().stream().filter(Objects::nonNull)
                .flatMap(submission -> submission.getResults() == null ? Stream.empty() : submission.getResults().stream())
                .filter(result -> isResultVisibleToStudent(result, manualResultsReleased)).map(Result::getId).collect(Collectors.toSet());
    }

    /**
     * A result is visible to the owning student when it is automatic, or when it is a completed manual assessment whose exercise has released its manual results (no assessment due
     * date, or one that has already passed). This is the same predicate the gated repository queries apply.
     *
     * @param result                the result to check, may be null
     * @param manualResultsReleased whether the exercise's manual results are already released
     * @return true if the student may see the result
     */
    private static boolean isResultVisibleToStudent(Result result, boolean manualResultsReleased) {
        return result != null && result.getId() != null
                && (AssessmentType.AUTOMATIC == result.getAssessmentType() || (result.getCompletionDate() != null && manualResultsReleased));
    }

    private void checkAccessPermissionAtLeastInstructor(StudentParticipation participation, User user) {
        Course course = findCourseFromParticipation(participation);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
    }

    private void checkAccessPermissionOwner(StudentParticipation participation, User user) {
        if (!authCheckService.isOwnerOfParticipation(participation)) {
            Course course = findCourseFromParticipation(participation);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        }
    }

    private Course findCourseFromParticipation(StudentParticipation participation) {
        Course course = null;
        Exercise exercise = participation.getExercise();
        if (exercise != null) {
            course = exercise.getCourseViaExerciseGroupOrCourseMember();
        }
        if (course == null) {
            Exercise loadedExercise = studentParticipationRepository.findByIdElseThrow(participation.getId()).getExercise();
            if (loadedExercise != null) {
                course = loadedExercise.getCourseViaExerciseGroupOrCourseMember();
            }
        }
        if (course == null) {
            throw new EntityNotFoundException("Course", "of participation " + participation.getId());
        }
        return course;
    }

    /**
     * fetches all submissions of a specific participation
     *
     * @param participationId the id of the participation
     * @return all submissions that belong to the participation
     */
    @GetMapping("participations/{participationId}/submissions")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<DetailedSubmissionDTO>> getSubmissionsOfParticipation(@PathVariable Long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        User user = userRepository.getUserWithAuthorities();
        checkAccessPermissionAtLeastInstructor(participation, user);
        List<Submission> submissions = submissionRepository.findAllWithResultsAndAssessorByParticipationId(participationId);
        DetailedParticipationDTO participationWithExercise = DetailedParticipationDTO.withExercise(participation);
        return ResponseEntity.ok(submissions.stream().map(submission -> DetailedSubmissionDTO.of(submission, participationWithExercise)).toList());
    }

    /**
     * GET /exercises/:exerciseId/participations/page : get paginated participations for the participation management view.
     *
     * @param exerciseId the exercise to query
     * @param search     search parameters including pagination, sorting, search term, filter, and score range
     * @return a paginated list of ParticipationManagementDTO with pagination headers
     */
    @GetMapping("exercises/{exerciseId}/participations/page")
    @EnforceAtLeastTutor
    public ResponseEntity<List<ParticipationManagementDTO>> getParticipationsPage(@PathVariable Long exerciseId, @Valid ParticipationSearchDTO search) {
        log.debug("REST request to get paged Participations for Exercise {}", exerciseId);

        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (exercise.isCourseExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        }
        else if (exercise.isExamExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        }

        boolean hideParticipant = shouldHideParticipantInformation(exercise, search.searchTerm(), search.sortedColumn());
        Page<ParticipationManagementDTO> page = participationService.findParticipationsForExercise(exercise, search);
        if (hideParticipant) {
            page = page.map(ParticipationManagementDTO::withoutParticipantInformation);
        }

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * GET /exercises/:exerciseId/participations/names : get participant names for all participations of the given exercise.
     *
     * @param exerciseId the exercise to query
     * @return list of {@link ParticipationNameExportDTO}, one entry per participation
     */
    @GetMapping("exercises/{exerciseId}/participations/names")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<ParticipationNameExportDTO>> getParticipationNamesForExport(@PathVariable Long exerciseId) {
        log.debug("REST request to export participation names for Exercise {}", exerciseId);

        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        List<ParticipationNameExportDTO> result = participationService.getParticipationNamesForExport(exercise);
        return ResponseEntity.ok(result);
    }

    private boolean canSeeParticipant(StudentParticipation participation) {
        return participationAuthCheckService.canSeeParticipantInformation(participation);
    }

    private boolean shouldHideParticipantInformation(Exercise exercise, String searchTerm, String sortedColumn) {
        if (participationAuthCheckService.canSeeParticipantInformation(exercise)) {
            return false;
        }
        // Filtering or ordering by identity would let tutors correlate an anonymous participation with a known student.
        if (StringUtils.hasText(searchTerm) || "participantName".equals(sortedColumn) || "participantIdentifier".equals(sortedColumn) || "buildPlanId".equals(sortedColumn)) {
            throw new AccessForbiddenException("Participant identity search and sorting are unavailable during anonymous assessment.");
        }
        return true;
    }

}
