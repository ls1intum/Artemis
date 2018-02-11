package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.security.AuthoritiesConstants;
import de.tum.in.www1.exerciseapp.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
        adminAuthority.setName("ROLE_ADMIN");
    }

    /**
     * Method used to check whether the current logged in user is instructor of this course
     *
     * @param course course to check the rights for
     * @return true, if user is instructor of this course, otherwise false
     */
    public boolean isInstructorInCourse(Course course) {
        log.debug("Request to check instructor access rights to course with id: {}", course.getId());
        //TODO execute a SQL query directly in the database to improve performance (using userRepository)
        User user = userService.getUserWithGroupsAndAuthorities();
        return user.getGroups().contains(course.getInstructorGroupName());
    }

    /**
     * Method used to check whether the current logged in user is teaching assistant of this course
     *
     * @param course course to check the rights for
     * @return true, if user is teaching assistant of this course, otherwise false
     */
    public boolean isTeachingAssistantInCourse(Course course) {
        log.debug("Request to check teaching assistant access rights to course with id: {}", course.getId());
        //TODO execute a SQL query directly in the database to improve performance (using userRepository)
        boolean result = userService.isTeachingAssistantInCourse(SecurityUtils.getCurrentUserLogin(), course.getTeachingAssistantGroupName());
        return result;
//        User user = userService.getUserWithGroupsAndAuthorities();
//        return user.getGroups().contains(course.getTeachingAssistantGroupName());
    }

    /**
     * method used to check whether the current logged in user is student of this course
     *
     * @param course course to check the rights for
     * @return true, if user is student of this course, otherwise false
     */
    public boolean isStudentInCourse(Course course) {
        log.debug("Request to check student access rights to course with id: {}", course.getId());
        //TODO execute a SQL query directly in the database to improve performance (using userRepository)
        User user = userService.getUserWithGroupsAndAuthorities();
        return user.getGroups().contains(course.getStudentGroupName());
    }

    /**
     * Method used to check whether the current logged in user is owner of this participation
     *
     * @param participation participation to check the rights for
     * @return true, if user is student is owner of this participation, otherwise false
     */
    public boolean isOwnerOfParticipation(Participation participation) {
        log.debug("Request to check student access rights to participation with id: {}", participation.getId());
        return participation.getStudent().getLogin().equals(SecurityUtils.getCurrentUserLogin());
    }

    /**
     * Method used to check whether the current logged in user is allowed to see this exercise
     *
     * @param exercise exercise to check the rights for
     * @return true, if user is allowed to see this exercise, otherwise false
     */
    public boolean isAllowedToSeeExercise(Exercise exercise) {
        //TODO execute a SQL query directly in the database to improve performance (using userRepository)
        log.debug("Request to check access rights to exercise with id: {}", exercise.getId());
        Course course = exercise.getCourse();
        return isAdmin() ||
            isInstructorInCourse(course) ||
            isTeachingAssistantInCourse(course) ||
            (isStudentInCourse(course) && exercise.isVisibleToStudents());
    }

    /**
     * Method used to check whether the current logged in user is application admin
     *
     * @return true, if user is admin, otherwise false
     */
    public boolean isAdmin() {
        log.debug("Request to check if user is admin");
        return SecurityUtils.isCurrentUserInRole(AuthoritiesConstants.ADMIN);
    }
}
