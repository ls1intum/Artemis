package de.tum.in.www1.artemis.web.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.lti.LtiDeepLinkingService;
import de.tum.in.www1.artemis.service.connectors.lti.LtiDynamicRegistrationService;

/**
 * REST controller to handle LTI13 launches.
 */
@RestController
@RequestMapping("/api")
@Profile("lti")
public class LtiResource {

    private final LtiDynamicRegistrationService ltiDynamicRegistrationService;

    private final LtiDeepLinkingService ltiDeepLinkingService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public static final String LOGIN_REDIRECT_CLIENT_PATH = "/lti/launch";

    public LtiResource(LtiDynamicRegistrationService ltiDynamicRegistrationService, CourseRepository courseRepository, AuthorizationCheckService authCheckService,
            LtiDeepLinkingService ltiDeepLinkingService) {
        this.ltiDynamicRegistrationService = ltiDynamicRegistrationService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.ltiDeepLinkingService = ltiDeepLinkingService;
    }

    @PostMapping("/lti13/dynamic-registration/{courseId}")
    @EnforceAtLeastInstructor
    public void lti13DynamicRegistration(@PathVariable Long courseId, @RequestParam(name = "openid_configuration") String openIdConfiguration,
            @RequestParam(name = "registration_token", required = false) String registrationToken) {

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        ltiDynamicRegistrationService.performDynamicRegistration(course, openIdConfiguration, registrationToken);
    }

    /**
     * Handles the HTTP POST request for LTI 1.3 Deep Linking. This endpoint is used for deep linking of LTI links
     * for exercises within a course. The method populates content items with the provided course and exercise identifiers,
     * builds a deep linking response, and returns the target link URI in a JSON object.
     *
     * @param courseId   The identifier of the course for which the deep linking is being performed.
     * @param exerciseId The identifier of the exercise to be included in the deep linking response.
     * @return A ResponseEntity containing a JSON object with the 'targetLinkUri' property set to the deep linking response target link.
     */
    @PostMapping("/lti13/deep-linking/{courseId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<String> lti13DeepLinking(@PathVariable Long courseId, @RequestParam(name = "exerciseId") String exerciseId) {
        ltiDeepLinkingService.populateContentItems(String.valueOf(courseId), exerciseId);
        String targetLink = ltiDeepLinkingService.buildLtiDeepLinkResponse();

        JsonObject json = new JsonObject();
        json.addProperty("targetLinkUri", targetLink);
        return ResponseEntity.ok(json.toString());
    }
}
