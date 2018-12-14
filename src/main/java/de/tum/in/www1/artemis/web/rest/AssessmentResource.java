package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.ResponseEntity;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

public abstract class AssessmentResource {
    protected final AuthorizationCheckService authCheckService;
    protected final UserService userService;

    public AssessmentResource(AuthorizationCheckService authCheckService, UserService userService) {
        this.authCheckService = authCheckService;
        this.userService = userService;
    }

    abstract String getEntityName();

    @Nullable
    <X> ResponseEntity<X> checkExercise(Exercise exercise) {
        Course course = exercise.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(getEntityName(), "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return forbidden();
        }
        return null;
    }
}
