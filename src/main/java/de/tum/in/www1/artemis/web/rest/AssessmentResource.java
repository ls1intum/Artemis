package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.*;

public abstract class AssessmentResource {

    protected final AuthorizationCheckService authCheckService;

    protected final UserService userService;

    public AssessmentResource(AuthorizationCheckService authCheckService, UserService userService) {
        this.authCheckService = authCheckService;
        this.userService = userService;
    }

    abstract String getEntityName();

    /**
     * @param exercise exercise to check privileges for
     * @throws AccessForbiddenException if current user is not at least teaching assistent in the given exercise
     * @throws BadRequestAlertException if no course is associated to the given exercise
     */
    void checkAuthorization(Exercise exercise) throws AccessForbiddenException, BadRequestAlertException {
        validateExercise(exercise);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            throw new AccessForbiddenException("Insufficient permission for course: " + exercise.getCourse().getTitle());
        }
    }

    void validateExercise(Exercise exercise) throws BadRequestAlertException {
        Course course = exercise.getCourse();
        if (course == null) {
            throw new BadRequestAlertException("The course belonging to this exercise does not exist", getEntityName(), "courseNotFound");
        }
    }
}
