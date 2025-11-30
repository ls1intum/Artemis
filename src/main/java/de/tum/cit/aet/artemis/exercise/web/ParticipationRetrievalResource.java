package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
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
     * GET /exercises/:exerciseId/participations : get all the participations for an exercise
     *
     * @param exerciseId        The participationId of the exercise
     * @param withLatestResults Whether the manual and latest {@link Result results} for the participations should also be fetched
     * @return A list of all participations for the exercise
     */
    @GetMapping("exercises/{exerciseId}/participations")
    @EnforceAtLeastTutor
    public ResponseEntity<Set<StudentParticipation>> getAllParticipationsForExercise(@PathVariable Long exerciseId,
            @RequestParam(defaultValue = "false") boolean withLatestResults) {
        log.debug("REST request to get all Participations for Exercise {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        Set<StudentParticipation> participations;
        if (withLatestResults) {
            participations = participationService.findByExerciseIdWithLatestSubmissionResultAndAssessmentNote(exercise.getId(), exercise.isTeamMode());
        }
        else {
            if (exercise.isTeamMode()) {
                participations = studentParticipationRepository.findWithTeamInformationByExerciseId(exerciseId);
            }
            else {
                participations = studentParticipationRepository.findByExerciseId(exerciseId);
            }

            Map<Long, Integer> submissionCountMap = studentParticipationRepository.countSubmissionsPerParticipationByExerciseIdAsMap(exerciseId);
            participations.forEach(participation -> participation.setSubmissionCount(submissionCountMap.get(participation.getId())));
        }
        Map<Long, Integer> submissionCountMap = studentParticipationRepository.countSubmissionsPerParticipationByExerciseIdAsMap(exerciseId);
        participations.forEach(participation -> participation.setSubmissionCount(submissionCountMap.get(participation.getId())));
        participations = participations.stream().filter(participation -> participation.getParticipant() != null).peek(participation -> {
            // remove unnecessary data to reduce response size
            participation.setExercise(null);
        }).collect(Collectors.toSet());

        return ResponseEntity.ok(participations);
    }

    /**
     * GET /participations/:participationId : get the participation for the given "participationId" including its latest result.
     *
     * @param participationId the participationId of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping("participations/{participationId}/with-latest-result")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentParticipation> getParticipationWithLatestResult(@PathVariable Long participationId) {
        log.debug("REST request to get Participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithResultsElseThrow(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);

        return new ResponseEntity<>(participation, HttpStatus.OK);
    }

    /**
     * GET /participations/:participationId : get the participation for the given "participationId".
     *
     * @param participationId the participationId of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping("participations/{participationId}")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentParticipation> getParticipationForCurrentUser(@PathVariable Long participationId) {
        log.debug("REST request to get participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithEagerTeamStudentsElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionOwner(participation, user);
        return new ResponseEntity<>(participation, HttpStatus.OK);
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
        if (participation.getExercise() != null && participation.getExercise().getCourseViaExerciseGroupOrCourseMember() != null) {
            return participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        }

        return studentParticipationRepository.findByIdElseThrow(participation.getId()).getExercise().getCourseViaExerciseGroupOrCourseMember();
    }

    /**
     * fetches all submissions of a specific participation
     *
     * @param participationId the id of the participation
     * @return all submissions that belong to the participation
     */
    @GetMapping("participations/{participationId}/submissions")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<Submission>> getSubmissionsOfParticipation(@PathVariable Long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionAtLeastInstructor(participation, user);
        List<Submission> submissions = submissionRepository.findAllWithResultsAndAssessorByParticipationId(participationId);
        return ResponseEntity.ok(submissions);
    }

}
