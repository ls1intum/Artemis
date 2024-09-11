package de.tum.cit.aet.artemis.web.rest;

import java.text.ParseException;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jwt.SignedJWT;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.repository.LtiPlatformConfigurationRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.connectors.lti.LtiDeepLinkingService;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import io.swagger.annotations.ApiParam;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller to handle LTI13 launches.
 */
@Profile("lti")
@RestController
@RequestMapping("api/")
public class LtiResource {

    private static final Logger log = LoggerFactory.getLogger(LtiResource.class);

    private final LtiDeepLinkingService ltiDeepLinkingService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    /**
     * Constructor for LtiResource.
     *
     * @param courseRepository      Repository for course data access.
     * @param authCheckService      Service for authorization checks.
     * @param ltiDeepLinkingService Service for LTI deep linking.
     */
    public LtiResource(CourseRepository courseRepository, AuthorizationCheckService authCheckService, LtiDeepLinkingService ltiDeepLinkingService,
            LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository) {
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.ltiDeepLinkingService = ltiDeepLinkingService;
        this.ltiPlatformConfigurationRepository = ltiPlatformConfigurationRepository;
    }

    /**
     * Handles the HTTP POST request for LTI 1.3 Deep Linking. This endpoint is used for deep linking of LTI links
     * for exercises within a course. The method populates content items with the provided course and exercise identifiers,
     * builds a deep linking response, and returns the target link URI in a JSON object.
     *
     * @param courseId             The identifier of the course for which the deep linking is being performed.
     * @param exerciseIds          The identifier of the exercises to be included in the deep linking response.
     * @param ltiIdToken           The token holding the deep linking information.
     * @param clientRegistrationId The identifier online of the course configuration.
     * @return A ResponseEntity containing a JSON object with the 'targetLinkUri' property set to the deep linking response target link.
     */
    @PostMapping("lti13/deep-linking/{courseId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<String> lti13DeepLinking(@PathVariable Long courseId, @RequestParam(name = "exerciseIds") Set<Long> exerciseIds,
            @RequestParam(name = "ltiIdToken") String ltiIdToken, @RequestParam(name = "clientRegistrationId") String clientRegistrationId) throws ParseException {
        log.info("LTI 1.3 Deep Linking request received for course {} with exercises {} for registrationId {}", courseId, exerciseIds, clientRegistrationId);

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        if (!course.isOnlineCourse() || course.getOnlineCourseConfiguration() == null) {
            throw new BadRequestAlertException("LTI is not configured for this course", "LTI", "ltiNotConfigured");
        }

        OidcIdToken idToken = new OidcIdToken(ltiIdToken, null, null, SignedJWT.parse(ltiIdToken).getJWTClaimsSet().getClaims());

        String targetLink = ltiDeepLinkingService.performDeepLinking(idToken, clientRegistrationId, courseId, exerciseIds);

        ObjectNode json = new ObjectMapper().createObjectNode();
        json.put("targetLinkUri", targetLink);
        return ResponseEntity.ok(json.toString());
    }

    /**
     * GET lti platforms : Get all configured lti platforms
     *
     * @param pageable Pageable
     * @return ResponseEntity containing a list of all lti platforms with status 200 (OK)
     */
    @GetMapping("lti-platforms")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<LtiPlatformConfiguration>> getAllConfiguredLtiPlatforms(@ApiParam Pageable pageable) {
        log.info("REST request to get all configured LTI platforms");
        Page<LtiPlatformConfiguration> platformsPage = ltiPlatformConfigurationRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), platformsPage);
        return new ResponseEntity<>(platformsPage.getContent(), headers, HttpStatus.OK);
    }
}
