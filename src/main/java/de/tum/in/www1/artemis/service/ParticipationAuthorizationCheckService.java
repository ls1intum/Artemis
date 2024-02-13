package de.tum.in.www1.artemis.service;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class ParticipationAuthorizationCheckService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationAuthorizationCheckService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final TeamRepository teamRepository;

    public ParticipationAuthorizationCheckService(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authCheckService, TeamRepository teamRepository) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;

        this.authCheckService = authCheckService;
        this.teamRepository = teamRepository;
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
            log.error("Cannot access null participation");
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
            log.error("Checking if user can access programming participation");
            return canAccessProgrammingParticipation(programmingExerciseParticipation, user);
        }
        else if (participation instanceof StudentParticipation studentParticipation) {
            log.error("Checking if user can access student participation");
            return userHasPermissionsToAccessParticipation(studentParticipation, user);
        }
        else {
            log.error("canAccessParticipation: unknown participation type");
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
            log.debug("User {} is owner of participation {}", user.getLogin(), participation.getId());
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
            log.debug("User {} is owner of participation {}", user.getLogin(), participation.getId());
            return true;
        }

        // if the user is not the owner of the participation, the user can only see it in case they are
        // a teaching assistant, an editor or an instructor of the course, or in case they are an admin
        final Course course = participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        return authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }
}
