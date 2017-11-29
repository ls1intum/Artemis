package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Authority;
import de.tum.in.www1.exerciseapp.domain.Course;
import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.domain.User;
import org.springframework.stereotype.Service;

/**
 * Service used to check whether user is authorized to perform actions on the entity.
 */
@Service
public class AuthorizationCheckService {

    private final UserService userService;
    private Authority adminAuthority;

    public AuthorizationCheckService(UserService userService) {
        this.userService = userService;
        adminAuthority = new Authority();
        adminAuthority.setName("ROLE_ADMIN");
    }

    public boolean isAuthorizedForCourse(Course course) {
        if(course == null) {
            return true;
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if(user.getGroups().contains(course.getStudentGroupName())
            || user.getGroups().contains(course.getTeachingAssistantGroupName())
            || user.getGroups().contains(course.getInstructorGroupName())
            || user.getAuthorities().contains(adminAuthority)
            || course.getTitle().equals("Archive")) {

            return true;
        }
        return false;
    }

    public boolean isAuthorizedForExercise(Exercise exercise) {
        if(exercise == null) {
            return true;
        }
        Course correspondingCourse = exercise.getCourse();
        if(correspondingCourse == null) {
            return true;
        }
        return isAuthorizedForCourse(correspondingCourse);
    }

}
