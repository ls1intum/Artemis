package de.tum.cit.aet.artemis.lti.web;

import static de.tum.cit.aet.artemis.core.config.Constants.LTI_ENABLED_PROPERTY_NAME;

import java.text.ParseException;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jwt.SignedJWT;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.lti.repository.LtiPlatformConfigurationRepository;
import de.tum.cit.aet.artemis.lti.service.DeepLinkingType;
import de.tum.cit.aet.artemis.lti.service.LtiDeepLinkingService;
import de.tum.cit.aet.artemis.lti.service.OnlineCourseConfigurationService;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller to handle LTI13 launches.
 */
@ConditionalOnProperty(value = LTI_ENABLED_PROPERTY_NAME, havingValue = "true")
@Lazy
@RestController
@RequestMapping("api/lti/")
public class LtiResource {

    private static final Logger log = LoggerFactory.getLogger(LtiResource.class);

    private final LtiDeepLinkingService ltiDeepLinkingService;

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

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
            OnlineCourseConfigurationService onlineCourseConfigurationService, LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository) {
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.ltiDeepLinkingService = ltiDeepLinkingService;
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.ltiPlatformConfigurationRepository = ltiPlatformConfigurationRepository;
    }

    /**
     * PUT courses/:courseId/online-course-configuration : Updates the onlineCourseConfiguration for the given course.
     *
     * @param courseId                  the id of the course to update
     * @param onlineCourseConfiguration the online course configuration to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated online course configuration
     */
    @PutMapping("courses/{courseId}/online-course-configuration")
    @EnforceAtLeastInstructor
    public ResponseEntity<OnlineCourseConfiguration> updateOnlineCourseConfiguration(@PathVariable Long courseId,
            @RequestBody OnlineCourseConfiguration onlineCourseConfiguration) {
        log.debug("REST request to update the online course configuration for Course : {}", courseId);

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        if (!course.isOnlineCourse()) {
            throw new BadRequestAlertException("Course must be online course", Course.ENTITY_NAME, "courseMustBeOnline");
        }

        if (!course.getOnlineCourseConfiguration().getId().equals(onlineCourseConfiguration.getId())) {
            throw new BadRequestAlertException("The onlineCourseConfigurationId does not match the id of the course's onlineCourseConfiguration",
                    OnlineCourseConfiguration.ENTITY_NAME, "idMismatch");
        }

        onlineCourseConfigurationService.validateOnlineCourseConfiguration(onlineCourseConfiguration);
        course.setOnlineCourseConfiguration(onlineCourseConfiguration);
        try {
            onlineCourseConfigurationService.addOnlineCourseConfigurationToLtiConfigurations(onlineCourseConfiguration);
        }
        catch (Exception ex) {
            log.error("Failed to add online course configuration to LTI configurations", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error when adding online course configuration to LTI configurations", ex);
        }

        courseRepository.save(course);

        return ResponseEntity.ok(onlineCourseConfiguration);
    }

    /**
     * Handles the HTTP POST request for LTI 1.3 Deep Linking. This endpoint enables deep linking of LTI links
     * for various course-related resources, such as exercises, lectures, competencies, learning paths, and IRIS integrations.
     * The method processes the provided course and resource identifiers, validates LTI configurations,
     * and constructs a deep linking response based on the specified resource type. The resulting deep link target URI
     * is returned in a JSON object.
     *
     * @param courseId             The identifier of the course for which deep linking is being performed.
     * @param resourceType         The type of resource being linked. Valid values are defined in the {@link DeepLinkingType} enum.
     * @param contentIds           A set of content identifiers (e.g., exercise IDs or lecture IDs) to be included in the deep linking response.
     *                                 This parameter is required for resource types that support multiple content items (e.g., EXERCISE, LECTURE).
     * @param ltiIdToken           The token containing the deep linking information.
     * @param clientRegistrationId The identifier of the course's online configuration.
     * @return A ResponseEntity containing a JSON object with the 'targetLinkUri' property set to the deep linking response target link.
     * @throws ParseException           If the LTI ID token cannot be parsed.
     * @throws BadRequestAlertException If LTI is not configured for the course, if no valid deep linking type is provided,
     *                                      or if content IDs are required but not provided for the specified resource type.
     */
    @PostMapping("lti13/deep-linking/{courseId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<String> lti13DeepLinking(@PathVariable Long courseId, @RequestParam(name = "resourceType") DeepLinkingType resourceType,
            @RequestParam(name = "contentIds", required = false) Set<Long> contentIds, @RequestParam(name = "ltiIdToken") String ltiIdToken,
            @RequestParam(name = "clientRegistrationId") String clientRegistrationId) throws ParseException {

        log.info("LTI 1.3 Deep Linking request received for course {} with resourceType: {}, contentIds: {}, registrationId: {}", courseId, resourceType, contentIds,
                clientRegistrationId);

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);

        if (!course.isOnlineCourse() || course.getOnlineCourseConfiguration() == null) {
            throw new BadRequestAlertException("LTI is not configured for this course", "LTI", "ltiNotConfigured");
        }

        OidcIdToken idToken = new OidcIdToken(ltiIdToken, null, null, SignedJWT.parse(ltiIdToken).getJWTClaimsSet().getClaims());

        String targetLink = switch (resourceType) {
            case EXERCISE, LECTURE, GROUPED_EXERCISE, GROUPED_LECTURE -> {
                if (contentIds == null || contentIds.isEmpty()) {
                    throw new BadRequestAlertException("Content IDs are required for resource type: " + resourceType, "LTI", "contentIdsRequired");
                }
                yield ltiDeepLinkingService.performDeepLinking(idToken, clientRegistrationId, courseId, contentIds, resourceType);
            }
            case COMPETENCY, LEARNING_PATH, IRIS -> ltiDeepLinkingService.performDeepLinking(idToken, clientRegistrationId, courseId, null, resourceType);
        };

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
    public ResponseEntity<List<LtiPlatformConfiguration>> getAllConfiguredLtiPlatforms(Pageable pageable) {
        log.info("REST request to get all configured LTI platforms");
        Page<LtiPlatformConfiguration> platformsPage = ltiPlatformConfigurationRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), platformsPage);
        return new ResponseEntity<>(platformsPage.getContent(), headers, HttpStatus.OK);
    }
}
