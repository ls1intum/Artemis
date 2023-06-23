package de.tum.in.www1.artemis.web.rest;

import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.lti.LtiDynamicRegistrationService;

/**
 * REST controller to handle LTI10 launches.
 */
@RestController
@RequestMapping("/api")
public class LtiResource {

    private final LtiDynamicRegistrationService ltiDynamicRegistrationService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public static final String LOGIN_REDIRECT_CLIENT_PATH = "/lti/launch";

    public LtiResource(LtiDynamicRegistrationService ltiDynamicRegistrationService, CourseRepository courseRepository, AuthorizationCheckService authCheckService) {
        this.ltiDynamicRegistrationService = ltiDynamicRegistrationService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    @PostMapping("/lti13/dynamic-registration/{courseId}")
    @EnforceAtLeastInstructor
    public void lti13DynamicRegistration(@PathVariable Long courseId, @RequestParam(name = "openid_configuration") String openIdConfiguration,
            @RequestParam(name = "registration_token", required = false) String registrationToken) {

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        ltiDynamicRegistrationService.performDynamicRegistration(course, openIdConfiguration, registrationToken);
    }
}
