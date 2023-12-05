package de.tum.in.www1.artemis.web.rest;

import java.text.ParseException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonObject;
import com.nimbusds.jwt.SignedJWT;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.lti.LtiDeepLinkingService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * REST controller to handle LTI13 launches.
 */
@RestController
@RequestMapping("/api")
@Profile("lti")
public class LtiResource {

    private final LtiDeepLinkingService ltiDeepLinkingService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public LtiResource(CourseRepository courseRepository, AuthorizationCheckService authCheckService, LtiDeepLinkingService ltiDeepLinkingService) {
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.ltiDeepLinkingService = ltiDeepLinkingService;
    }

    /**
     * Handles the HTTP POST request for LTI 1.3 Deep Linking. This endpoint is used for deep linking of LTI links
     * for exercises within a course. The method populates content items with the provided course and exercise identifiers,
     * builds a deep linking response, and returns the target link URI in a JSON object.
     *
     * @param courseId             The identifier of the course for which the deep linking is being performed.
     * @param exerciseId           The identifier of the exercise to be included in the deep linking response.
     * @param ltiIdToken           The token holding the deep linking information.
     * @param clientRegistrationId The identifier online of the course configuration.
     * @return A ResponseEntity containing a JSON object with the 'targetLinkUri' property set to the deep linking response target link.
     */
    @PostMapping("/lti13/deep-linking/{courseId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<String> lti13DeepLinking(@PathVariable Long courseId, @RequestParam(name = "exerciseId") String exerciseId,
            @RequestParam(name = "ltiIdToken") String ltiIdToken, @RequestParam(name = "clientRegistrationId") String clientRegistrationId) throws ParseException {

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        if (!course.isOnlineCourse() || course.getOnlineCourseConfiguration() == null) {
            throw new BadRequestAlertException("LTI is not configured for this course", "LTI", "ltiNotConfigured");
        }

        OidcIdToken idToken = new OidcIdToken(ltiIdToken, null, null, SignedJWT.parse(ltiIdToken).getJWTClaimsSet().getClaims());

        String targetLink = ltiDeepLinkingService.performDeepLinking(idToken, clientRegistrationId, courseId, Long.valueOf(exerciseId));

        JsonObject json = new JsonObject();
        json.addProperty("targetLinkUri", targetLink);
        return ResponseEntity.ok(json.toString());
    }
}
