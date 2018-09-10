package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service used to check whether user is authorized to perform actions on the entity.
 */
@Service
public class AuthorizationCheckService {

    private final Logger log = LoggerFactory.getLogger(AuthorizationCheckService.class);

    private final UserService userService;
    private Authority adminAuthority;

    public AuthorizationCheckService(UserService userService) {
        this.userService = userService;
        adminAuthority = new Authority();
        adminAuthority.setName(AuthoritiesConstants.ADMIN);
    }

    public <T extends Exercise> boolean isAtLeastTeachingAssistantForExercise(Optional<T> exercise) {
        if (exercise.isPresent()) {
            return isAtLeastTeachingAssistantForExercise(exercise.get());
        }
        else {
            return false;
        }
    }

    public boolean isAtLeastTeachingAssistantForExercise(Exercise exercise) {
        return isAtLeastTeachingAssistantInCourse(exercise.getCourse(), null);
    }

    public boolean isAtLeastTeachingAssistantForExercise(Optional<Exercise> exercise, User user) {
        if (exercise.isPresent()) {
            return isAtLeastTeachingAssistantForExercise(exercise.get(), user);
        }
        else {
            return false;
        }
    }

    public boolean isAtLeastTeachingAssistantForExercise(Exercise exercise, User user) {
        return isAtLeastTeachingAssistantInCourse(exercise.getCourse(), user);
    }

    public boolean isAtLeastTeachingAssistantInCourse(Course course, User user) {
        if (user == null || user.getGroups() == null) {
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return user.getGroups().contains(course.getInstructorGroupName()) ||
            user.getGroups().contains(course.getTeachingAssistantGroupName()) ||
            isAdmin();

    }

    /**
     * Method used to check whether the current logged in user is instructor of this course
     *
     * @param course course to check the rights for
     * @return true, if user is instructor of this course, otherwise false
     */
    public boolean isInstructorInCourse(Course course, User user) {
        if (user == null || user.getGroups() == null) {
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return user.getGroups().contains(course.getInstructorGroupName());
    }

    /**
     * Method used to check whether the current logged in user is teaching assistant of this course
     *
     * @param course course to check the rights for
     * @return true, if user is teaching assistant of this course, otherwise false
     */
    public boolean isTeachingAssistantInCourse(Course course, User user) {
        if (user == null || user.getGroups() == null) {
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return user.getGroups().contains(course.getTeachingAssistantGroupName());
    }

    /**
     * method used to check whether the current logged in user is student of this course
     *
     * @param course course to check the rights for
     * @return true, if user is student of this course, otherwise false
     */
    public boolean isStudentInCourse(Course course, User user) {
        if (user == null || user.getGroups() == null) {
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return user.getGroups().contains(course.getStudentGroupName());
    }

    /**
     * Method used to check whether the current logged in user is owner of this participation
     *
     * @param participation participation to check the rights for
     * @return true, if user is student is owner of this participation, otherwise false
     */
    public boolean isOwnerOfParticipation(Participation participation) {
        return participation.getStudent().getLogin().equals(SecurityUtils.getCurrentUserLogin().get());
    }

    /**
     * Method used to check whether the current logged in user is allowed to see this exercise
     *
     * @param exercise exercise to check the rights for
     * @return true, if user is allowed to see this exercise, otherwise false
     */
    public boolean isAllowedToSeeExercise(Exercise exercise, User user) {
        if (isAdmin()) { return true; }
        if (user == null || user.getGroups() == null) {
            user = userService.getUserWithGroupsAndAuthorities();
        }
        Course course = exercise.getCourse();
        return  isInstructorInCourse(course, user) ||
                isTeachingAssistantInCourse(course, user) ||
                (isStudentInCourse(course, user) && exercise.isVisibleToStudents());
    }

    /**
     * Method used to check whether the current logged in user is application admin
     *
     * @return true, if user is admin, otherwise false
     */
    public boolean isAdmin() {
        return SecurityUtils.isCurrentUserInRole(AuthoritiesConstants.ADMIN);
    }
}
