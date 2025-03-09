package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.ParticipationInterface;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;

@Profile(PROFILE_CORE)
@Service
public class ParticipationAuthorizationCheckService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationAuthorizationCheckService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final TeamRepository teamRepository;

    private final ExerciseDateService exerciseDateService;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final ParticipationRepository participationRepository;

    private final SubmissionRepository submissionRepository;

    private final StudentExamRepository studentExamRepository;

    public ParticipationAuthorizationCheckService(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authCheckService, TeamRepository teamRepository, ExerciseDateService exerciseDateService,
            SubmissionPolicyRepository submissionPolicyRepository, ParticipationRepository participationRepository, SubmissionRepository submissionRepository,
            StudentExamRepository studentExamRepository) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;

        this.authCheckService = authCheckService;
        this.teamRepository = teamRepository;
        this.exerciseDateService = exerciseDateService;
        this.submissionPolicyRepository = submissionPolicyRepository;
        this.participationRepository = participationRepository;
        this.submissionRepository = submissionRepository;
        this.studentExamRepository = studentExamRepository;
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
     * Checks if the current user is allowed to access the participation.
     *
     * @param participation Some participation.
     * @return True, if the current user is allowed to access the participation; false otherwise.
     */
    public boolean canAccessParticipation(@NotNull final ParticipationInterface participation) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        return canAccessParticipation(participation, user);
    }

    /**
     * Checks if the user is allowed to access the participation.
     *
     * @param participation Some participation.
     * @param user          The user that wants to access the participation.
     * @return True, if the user is allowed to access the participation; false otherwise.
     */
    public boolean canAccessParticipation(@NotNull final ParticipationInterface participation, final User user) {
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
        final Course course = participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        return authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }

    /**
     * Determines whether a given programming exercise participation is locked.
     * A participation is considered locked if:
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
        if (exerciseDateService.isAfterDueDate(participation, exercise)) {
            return true;
        }

        if (exercise.isExamExercise()) {
            var studentExamSubmitted = studentExamRepository.isSubmitted(exercise.getExam().getId(), participation.getParticipant().getId());
            // if the corresponding student exam was already submitted, the participation is locked
            // if the student exam does not exist yet, the participation should not exist either
            return studentExamSubmitted.orElse(true);
        }

        var submissionPolicy = submissionPolicyRepository.findByProgrammingExerciseId(exercise.getId());

        // NOTE: in case a submission policy is set for the corresponding programming exercise, set the locked value of the participation properly, in particular for exams
        if (submissionPolicy != null && submissionPolicy.isActive() && submissionPolicy instanceof LockRepositoryPolicy) {

            // the participation is locked when the submission count is at least as high as the submission limit
            return submissionRepository.countByParticipationId(participation.getId()) >= submissionPolicy.getSubmissionLimit();
        }
        return false;
    }
}
