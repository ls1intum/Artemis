package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
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
     * @param course course to check the rights for
     * @return true, if user is instructor of this course, otherwise false
     */
    public boolean isInstructorInCourse(Course course) {
        log.debug("Request to check instructor access rights to course with id: {}", course.getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        return user.getGroups().contains(course.getInstructorGroupName());
    }

    /**
     * Method used to check whether the current logged in user is teaching assistant of this course
     * @param course course to check the rights for
     * @return true, if user is teaching assistant of this course, otherwise false
     */
    public boolean isTeachingAssistantInCourse(Course course) {
        log.debug("Request to check teaching assistant access rights to course with id: {}", course.getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        return user.getGroups().contains(course.getTeachingAssistantGroupName());
    }

    /**
     * Method used to check whether the current logged in user is student of this course
     * @param course course to check the rights for
     * @return true, if user is student of this course, otherwise false
     */
    public boolean isStudentInCourse(Course course) {
        log.debug("Request to check student access rights to course with id: {}", course.getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        return user.getGroups().contains(course.getStudentGroupName());
    }

    /**
     * Method used to check whether the current logged in user is owner of this participation
     * @param participation participation to check the rights for
     * @return true, if user is student is owner of this participation, otherwise false
     */
    public boolean isOwnerOfParticipation(Participation participation) {
        log.debug("Request to check student access rights to participation with id: {}", participation.getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        return participation.getStudent().getLogin().equals(user.getLogin());
    }


    /**
     * Method used to check whether the current logged in user is application admin
     * @return true, if user is admin, otherwise false
     */
    public boolean isAdmin() {
        log.debug("Request to check if user is admin");
        User user = userService.getUserWithGroupsAndAuthorities();
        return user.getAuthorities().contains(adminAuthority);
    }

}
