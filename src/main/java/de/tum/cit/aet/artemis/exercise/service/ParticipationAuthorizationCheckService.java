package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.api.ExamApi;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.ParticipationInterface;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ParticipationAuthorizationCheckService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationAuthorizationCheckService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final TeamRepository teamRepository;

    private final ExerciseDateService exerciseDateService;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final SubmissionRepository submissionRepository;

    private final Optional<StudentExamApi> studentExamApi;

    private final Optional<ExamApi> examApi;

    private final StudentParticipationRepository studentParticipationRepository;

    public ParticipationAuthorizationCheckService(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authCheckService, TeamRepository teamRepository, ExerciseDateService exerciseDateService,
            SubmissionPolicyRepository submissionPolicyRepository, SubmissionRepository submissionRepository, Optional<StudentExamApi> studentExamApi, Optional<ExamApi> examApi,
            StudentParticipationRepository studentParticipationRepository) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.teamRepository = teamRepository;
        this.exerciseDateService = exerciseDateService;
        this.submissionPolicyRepository = submissionPolicyRepository;
        this.submissionRepository = submissionRepository;
        this.studentExamApi = studentExamApi;
        this.examApi = examApi;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Whether authorized course staff may see participant identities and identifying repository metadata.
     * Callers must check access to the exercise separately.
     *
     * @param exercise the exercise being accessed
     * @return whether the current user may see identities
     */
    public boolean canSeeParticipantInformation(Exercise exercise) {
        return authCheckService.isAtLeastInstructorForExercise(exercise) || !exerciseDateService.isAssessmentAnonymizationActive(exercise);
    }

    /**
     * Keeps an owner's direct access to their own data during anonymous assessment.
     *
     * @param participation the participation being accessed
     * @return whether the current user may see its identifying data
     */
    public boolean canSeeParticipantInformation(StudentParticipation participation) {
        return authCheckService.isOwnerOfParticipation(participation) || canSeeParticipantInformation(participation.getExercise());
    }

    /**
     * Decides whether the results of an exam exercise participation have to be hidden from the participation's student.
     * <p>
     * Exam results stay hidden until the student may see them (the exam is running for a programming exercise, or the
     * results have been published), unlike a course exercise whose manual results are released by the assessment due
     * date. Read endpoints that serve a student participation of an exam exercise must apply this before returning any
     * result, so that the score of a not-yet-published assessment does not leak. It is owner-based, like the sibling
     * programming endpoints: a tutor or instructor reading a student's exam participation sees the same hiding and uses
     * the dedicated assessment endpoints to access unpublished results.
     *
     * @param participation the student participation whose results are about to be served
     * @return true if the results must be hidden, false otherwise (also for course exercises and test runs)
     */
    public boolean shouldHideExamExerciseResults(StudentParticipation participation) {
        // Test-run results are never hidden as a whole: the conductor is the instructor who created the test run. Its feedback is still filtered for the student view, see
        // ResultService#filterSensitiveInformationIfNecessary.
        if (participation.isTestRun()) {
            return false;
        }
        Exercise exercise = participation.getExercise();
        if (!exercise.isExamExercise() || exercise.isTestExamExercise()) {
            return false;
        }
        StudentExamApi studentExamApiValue = studentExamApi.orElseThrow(() -> new ExamApiNotPresentException(StudentExamApi.class));
        ExamApi examApiValue = examApi.orElseThrow(() -> new ExamApiNotPresentException(ExamApi.class));
        // Exam exercises are individual, so the student exam is resolved by the exercise and the participation's student, as the programming retrieval endpoints have always done.
        User student = participation.getStudent().orElseThrow(() -> new EntityNotFoundException("Participation with id " + participation.getId() + " does not have a student!"));
        StudentExam studentExam = studentExamApiValue.findByExerciseIdAndUserId(exercise.getId(), student.getId())
                .orElseThrow(() -> new EntityNotFoundException("Participation " + participation.getId() + " does not have a student exam!"));
        return !examApiValue.shouldStudentSeeResult(studentExam, participation);
    }

    /**
     * Checks if the current user is allowed to access the given participation.
     * <p>
     * Throws an {@link AccessForbiddenException} if not.
     *
     * @param participation Some participation.
     */
    public void checkCanAccessParticipationElseThrow(final ParticipationInterface participation) {
        if (participation == null) {
            throw new AccessForbiddenException("participation");
        }
        else if (!canAccessParticipation(participation)) {
            throw new AccessForbiddenException("participation", participation.getId());
        }
    }

    /**
     * Checks if the current user is allowed to access the student participation
     * NOTE: this does not work for template or solution participations of programming exercises!
     * 1. Either the user owns the participation (as student) or 2. is at least a teaching assistant in the course of the participation.
     *
     * @param studentParticipationId The id of the student participation to check access for.
     * @return True, if the current user is allowed to access the participation; false otherwise.
     */
    public boolean canAccessStudentParticipation(long studentParticipationId) {
        String userLogin = userRepository.getCurrentUserLogin();
        // 1. Check if the user owns the participation
        // 1a. StudentParticipation: the user is the owner of the participation
        // 1b. TeamParticipation: the user is a member of the team
        boolean isOwner = studentParticipationRepository.isOwnerOfStudentParticipation(userLogin, studentParticipationId);
        if (isOwner) {
            return true;
        }
        // 2. Check if the user is at least a teaching assistant of the course
        return userRepository.isAtLeastTeachingAssistantInParticipation(userLogin, studentParticipationId) || authCheckService.isCurrentUserAdminAccessEnabled();
    }

    /**
     * Checks if the current user is allowed to access the participation.
     *
     * @param participation Some participation.
     * @return True, if the current user is allowed to access the participation; false otherwise.
     */
    public boolean canAccessParticipation(@NonNull final ParticipationInterface participation) {
        final User user = userRepository.getUserWithAuthorities();
        return canAccessParticipation(participation, user);
    }

    /**
     * Checks if the user is allowed to access the participation.
     *
     * @param participation Some participation.
     * @param user          The user that wants to access the participation.
     * @return True, if the user is allowed to access the participation; false otherwise.
     */
    public boolean canAccessParticipation(@NonNull final ParticipationInterface participation, final User user) {
        if (participation instanceof StudentParticipation studentParticipation && studentParticipation.getParticipant() instanceof Team team) {
            // eager load the team with students so their information can be used for the access check below
            studentParticipation.setParticipant(teamRepository.findWithStudentsByIdElseThrow(team.getId()));
        }
        if (participation instanceof ProgrammingExerciseParticipation programmingExerciseParticipation) {
            return canAccessProgrammingParticipation(programmingExerciseParticipation, user);
        }
        else if (participation instanceof StudentParticipation studentParticipation) {
            return userHasPermissionsToAccessParticipation(studentParticipation, user);
        }
        else {
            // null or unknown participation type (should not exist unless class hierarchy changes): do not give access
            return false;
        }
    }

    /**
     * Check if the currently logged-in user can access a given participation by accessing the exercise and course connected to this participation
     * The method will treat the participation types differently:
     * - ProgrammingExerciseStudentParticipations should only be accessible by its owner (student) or users with at least the role TA in the courses.
     * - Template/SolutionParticipations should only be accessible for users with at least the role TA in the courses.
     *
     * @param participation to check permissions for.
     * @return true if the user can access the participation, false if not. Also returns false if the participation is not from a programming exercise.
     */
    private boolean canAccessProgrammingParticipation(final ProgrammingExerciseParticipation participation, final User user) {
        // If the current user is owner of the participation, they are allowed to access it
        if (participation instanceof ProgrammingExerciseStudentParticipation studentParticipation && studentParticipation.isOwnedBy(user)) {
            return true;
        }

        final ProgrammingExercise programmingExercise = programmingExerciseRepository.getProgrammingExerciseFromParticipation(participation);
        if (programmingExercise == null) {
            log.error("canAccessParticipation: could not find programming exercise of participation id {}", participation.getId());
            // Cannot access a programming participation that has no programming exercise associated with it
            return false;
        }

        return authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise, user);
    }

    /**
     * Check if a user has permissions to access a certain participation. This includes not only the owner of the participation but also the TAs and instructors of the course.
     *
     * @param participation to access
     * @return does user has permissions to access participation
     */
    private boolean userHasPermissionsToAccessParticipation(final StudentParticipation participation, final User user) {
        if (authCheckService.isOwnerOfParticipation(participation)) {
            return true;
        }

        // if the user is not the owner of the participation, the user can only see it in case they are
        // a teaching assistant, an editor or an instructor of the course, or in case they are an admin
        final Course course = participation.getExercise().getCourseViaExerciseGroupOrCourseMemberElseThrow();
        return authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }

    /**
     * Whether the participation is locked, for a caller holding a projection of the exercise.
     * <p>
     * The same decision as {@link #isLocked(ProgrammingExerciseStudentParticipation, ProgrammingExercise)}, for the
     * git request path. Everything it needs from the exercise is an id, a flag or a date, so the projection carries
     * all of it.
     *
     * @param participation the student's participation
     * @param exercise      the projected exercise
     * @return true if the participation is locked
     */
    public boolean isLocked(ProgrammingExerciseStudentParticipation participation, GitRepositoryAccessDTO exercise) {
        if (participation.isPracticeMode()) {
            return false;
        }

        if (exerciseDateService.isAfterDueDate(participation, exercise)) {
            return true;
        }

        return isLockedByExamOrSubmissionPolicy(participation, exercise.exerciseId(), exercise.isExamExercise(), exercise::isTestExamExercise, () -> exercise.examId());
    }

    /**
     * Determines whether a given programming exercise participation is locked.
     * A practice mode participation is never locked.
     * Otherwise, a participation is considered locked if:
     * <ul>
     * <li>The due date of the exercise has passed.</li>
     * <li>The exercise is an exam exercise, and:
     * <ul>
     * <li>The associated student exam has already been submitted.</li>
     * <li>The student exam does not exist (indicating an inconsistency).</li>
     * </ul>
     * </li>
     * <li>A submission policy with a submission limit is active, and the submission count
     * has reached or exceeded the limit.</li>
     * </ul>
     *
     * @param participation The student participation for the programming exercise.
     * @param exercise      The programming exercise to check.
     * @return {@code true} if the participation is locked based on the conditions above; {@code false} otherwise.
     */
    public boolean isLocked(ProgrammingExerciseStudentParticipation participation, ProgrammingExercise exercise) {
        if (participation.isPracticeMode()) {
            return false;
        }

        if (exerciseDateService.isAfterDueDate(participation, exercise)) {
            return true;
        }

        return isLockedByExamOrSubmissionPolicy(participation, exercise.getId(), exercise.isExamExercise(), exercise::isTestExamExercise, () -> exercise.getExam().getId());
    }

    /** Resolves the exam id lazily because repeated test exams only require the participation id. */
    private boolean isLockedByExamOrSubmissionPolicy(ProgrammingExerciseStudentParticipation participation, Long exerciseId, boolean examExercise,
            java.util.function.BooleanSupplier testExam, java.util.function.Supplier<Long> examId) {
        if (examExercise) {
            var api = studentExamApi.orElseThrow(() -> new ExamApiNotPresentException(StudentExamApi.class));
            Optional<Boolean> submitted = testExam.getAsBoolean() ? api.isSubmitted(participation.getId()) : api.isSubmitted(examId.get(), participation.getParticipant().getId());
            // A missing student exam cannot have an unlocked participation.
            return submitted.orElse(true);
        }
        var policy = submissionPolicyRepository.findByProgrammingExerciseId(exerciseId);
        return policy != null && policy.isActive() && policy instanceof LockRepositoryPolicy
                && submissionRepository.countByParticipationId(participation.getId()) >= policy.getSubmissionLimit();
    }
}
