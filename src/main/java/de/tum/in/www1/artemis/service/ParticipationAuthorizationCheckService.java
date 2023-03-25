package de.tum.in.www1.artemis.service;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class ParticipationAuthorizationCheckService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationAuthorizationCheckService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    public ParticipationAuthorizationCheckService(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authCheckService) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;

        this.authCheckService = authCheckService;
    }

    /**
     * Checks if the current user is allowed to access the given participation.
     * <p>
     * Throws an {@link AccessForbiddenException} if not.
     *
     * @param participation Some participation.
     */
    public void checkCanAccessParticipationElseThrow(@NotNull final ParticipationInterface participation) {
        if (!canAccessParticipation(participation)) {
            throw new AccessForbiddenException("participation", participation.getId());
        }
    }

    /**
     * Checks if the current usr is allowed to access the given participation.
     *
     * @param participation Some participation.
     * @return True, if the user is allowed to access the participation; false otherwise.
     */
    public boolean canAccessParticipation(@NotNull final ParticipationInterface participation) {
        if (participation instanceof StudentParticipation studentParticipation) {
            return canAccessParticipation(studentParticipation);
        }
        else if (participation instanceof ProgrammingExerciseParticipation programmingExerciseParticipation) {
            return canAccessParticipation(programmingExerciseParticipation);
        }
        else {
            // unknown participation type, should not exist unless class hierarchy changes: do not give access
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
    private boolean canAccessParticipation(ProgrammingExerciseParticipation participation) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        // If the current user is owner of the participation, they are allowed to access it
        if (participation instanceof ProgrammingExerciseStudentParticipation studentParticipation && studentParticipation.isOwnedBy(user)) {
            return true;
        }

        ProgrammingExercise programmingExercise = programmingExerciseRepository.getProgrammingExerciseFromParticipation(participation);
        if (programmingExercise == null) {
            log.error("canAccessParticipation: could not find programming exercise of participation id {}", participation.getId());
            // Cannot access a programming participation that has no programming exercise associated with it
            return false;
        }

        return authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise, user);
    }

    /**
     * Check if a participation can be accessed with the current user.
     *
     * @param participation to access
     * @return can user access participation
     */
    private boolean canAccessParticipation(StudentParticipation participation) {
        return participation != null && userHasPermissionsToAccessParticipation(participation);
    }

    /**
     * Check if a user has permissions to access a certain participation. This includes not only the owner of the participation but also the TAs and instructors of the course.
     *
     * @param participation to access
     * @return does user has permissions to access participation
     */
    private boolean userHasPermissionsToAccessParticipation(StudentParticipation participation) {
        if (authCheckService.isOwnerOfParticipation(participation)) {
            return true;
        }

        // if the user is not the owner of the participation, the user can only see it in case they are
        // a teaching assistant, an editor or an instructor of the course, or in case they are an admin
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        return authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }
}
