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
     * Method used to check whether the current logged in user is authorized to view this course
     * @param course course to check the rights for
     * @return true, if user is authorized to view this course, otherwise false
     */
    public boolean isAuthorizedForCourse(Course course) {
        log.debug("Request to check access rights to course with id: {}", course.getId());
        if(course == null) {
            return true;
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if(user.getGroups().contains(course.getStudentGroupName())
            || user.getGroups().contains(course.getTeachingAssistantGroupName())
            || user.getGroups().contains(course.getInstructorGroupName())
            || user.getAuthorities().contains(adminAuthority)) {
            return true;
        }
        return false;
    }


    /**
     * Method used to check whether the current logged in user is authorized to view this exercise
     * @param exercise exercise to check the rights for
     * @return true, if user is authorized to view this exercise, otherwise false
     */
    public boolean isAuthorizedForExercise(Exercise exercise) {
        log.debug("Request to check access rights to exercise with id: {}", exercise.getId());
        if(exercise == null) {
            return true;
        }
        Course correspondingCourse = exercise.getCourse();
        return isAuthorizedForCourse(correspondingCourse);
    }

    /**
     * Method used to check whether the current logged in user is authorized to view this participation
     * @param participation participation to check the rights for
     * @return true, if user is authorized to view this participation, otherwise false
     */
    public boolean isAuthorizedForParticipation(Participation participation) {
        log.debug("Request to check access rights to participation with id: {}", participation.getId());
        if(participation == null) {
            return true;
        }
        Exercise correspondingExercise = participation.getExercise();
        if(correspondingExercise == null) {
            return true;
        }
        return isAuthorizedForCourse(correspondingExercise.getCourse());
    }

}
