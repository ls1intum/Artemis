package de.tum.in.www1.artemis.web.rest;

import org.jetbrains.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.*;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

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
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }
        return null;
    }


    /**
     * @param exercise exercise to check privileges for
     * @throws AccessForbiddenException if current user is not at least teaching assistent in the given exercise
     * @throws BadRequestAlertException if no course is associated to the given exercise
     */
    void checkAuthorization(Exercise exercise) throws AccessForbiddenException, BadRequestAlertException {
        validateExercise(exercise);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            throw new AccessForbiddenException("Unsufficient permission for course: " + exercise.getCourse().getTitle());
        }
    }


    private void validateExercise(Exercise exercise) throws BadRequestAlertException {
        Course course = exercise.getCourse();
        if (course == null) {
            throw new BadRequestAlertException("The course belonging to this modeling exercise does not exist", getEntityName(), "courseNotFound");
        }
    }
}
