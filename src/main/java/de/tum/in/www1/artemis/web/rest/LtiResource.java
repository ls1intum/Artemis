package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.util.StringUtils;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseLtiConfigurationDTO;
import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Created by Josias Montag on 22.09.16.
 * <p>
 * REST controller for receiving LTI messages.
 */
@RestController
@RequestMapping("/api")
public class LtiResource {

    private final Logger log = LoggerFactory.getLogger(LtiResource.class);

    private final LtiService ltiService;

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final TokenProvider tokenProvider;

    private final AuthorizationCheckService authCheckService;

    public static final String LOGIN_REDIRECT_CLIENT_PATH = "/lti/launch";

    public LtiResource(LtiService ltiService, UserRepository userRepository, ExerciseRepository exerciseRepository, CourseRepository courseRepository, TokenProvider tokenProvider,
            AuthorizationCheckService authCheckService) {
        this.ltiService = ltiService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
        this.tokenProvider = tokenProvider;
        this.authCheckService = authCheckService;
    }

    /**
     * POST lti/launch/:exerciseId : Launch the exercise app using request by a LTI consumer. Redirects the user to the exercise on success.
     *
     * @param launchRequest the LTI launch request (ExerciseLtiConfigurationDTO)
     * @param exerciseId    the id of the exercise the user wants to open
     * @param request       HTTP request
     * @param response      HTTP response
     * @throws IOException If an input or output exception occurs
     */
    @PostMapping(value = "lti/launch/{exerciseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public void launch(@ModelAttribute LtiLaunchRequestDTO launchRequest, @PathVariable("exerciseId") Long exerciseId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        log.info("/lti/launch/{} with launch request: {}", exerciseId, launchRequest);

        log.debug("Request header X-Forwarded-Proto: {}", request.getHeader("X-Forwarded-Proto"));
        log.debug("Request header X-Forwarded-For: {}", request.getHeader("X-Forwarded-For"));

        if (!request.getRequestURL().toString().startsWith("https")) {
            log.error("The request url {} does not start with 'https'. Verification of the request will most probably fail. Please double check your loadbalancer (e.g. nginx) "
                    + "configuration and your Spring configuration (e.g. application.yml) with respect to proxy_set_header X-Forwarded-Proto and forward-headers-strategy: "
                    + "native", request.getRequestURL().toString());
        }

        // Check if exercise ID is valid
        Optional<Exercise> optionalExercise = exerciseRepository.findById(exerciseId);
        if (optionalExercise.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Exercise not found");
            return;
        }

        Exercise exercise = optionalExercise.get();
        log.debug("found exercise {}", exercise.getTitle());

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        OnlineCourseConfiguration onlineCourseConfiguration = course.getOnlineCourseConfiguration();
        if (onlineCourseConfiguration == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Exercise is not part of course configured for LTI");
            return;
        }

        log.debug("Try to verify LTI Oauth Request");
        // Verify request
        String error = ltiService.verifyRequest(request, onlineCourseConfiguration);
        if (error != null) {
            log.warn("Failed verification for launch request : {}", launchRequest);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, error + ". Cannot launch exercise " + exerciseId + ". " + "Please contact an admin or try again.");
            return;
        }
        log.debug("Oauth Verification succeeded");

        try {
            ltiService.authenticateLtiUser(launchRequest.getLis_person_contact_email_primary(), launchRequest.getUser_id(),
                    createUsernameFromLaunchRequest(launchRequest, onlineCourseConfiguration), getUserFirstNameFromLaunchRequest(launchRequest),
                    getUserLastNameFromLaunchRequest(launchRequest), launchRequest.getCustom_require_existing_user(), launchRequest.getCustom_lookup_user_by_email());
            User user = userRepository.getUserWithGroupsAndAuthorities();
            ltiService.onSuccessfulLtiAuthentication(user, launchRequest.getUser_id(), exercise);
            ltiService.saveLtiOutcomeUrl(user, exercise, launchRequest.getLis_outcome_service_url(), launchRequest.getLis_result_sourcedid());
        }
        catch (InternalAuthenticationServiceException ex) {
            log.error("Error during LTI launch request of exercise {} for launch request: {}", exercise.getTitle(), launchRequest, ex);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            return;
        }
        catch (Exception ex) {
            log.error("Error during LTI launch request of exercise {} for launch request: {}", exercise.getTitle(), launchRequest, ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
            return;
        }

        log.debug("handleLaunchRequest done");

        sendRedirect(request, response, exercise);
    }

    /**
     * Redirects the launch request to Artemis.
     * Note: The following redirect URL has to match the URL in user-route-access-service.ts in the method canActivate(...)
     *
     * @param request       HTTP request
     * @param response      HTTP response
     * @param exercise      The exercise to redirect to
     * @throws IOException  If an input or output exception occurs
     *
     */
    private void sendRedirect(HttpServletRequest request, HttpServletResponse response, Exercise exercise) throws IOException {

        UriComponentsBuilder redirectUrlComponentsBuilder = UriComponentsBuilder.newInstance().scheme(request.getScheme()).host(request.getServerName());
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            redirectUrlComponentsBuilder.port(request.getServerPort());
        }
        redirectUrlComponentsBuilder.pathSegment("courses").pathSegment(exercise.getCourseViaExerciseGroupOrCourseMember().getId().toString()).pathSegment("exercises")
                .pathSegment(exercise.getId().toString());

        User user = userRepository.getUser();

        if (!user.getActivated()) {
            redirectUrlComponentsBuilder.queryParam("initialize", "");

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String jwt = tokenProvider.createToken(authentication, true);
            log.debug("created jwt token: {}", jwt);
            redirectUrlComponentsBuilder.queryParam("jwt", jwt);
        }
        else {
            redirectUrlComponentsBuilder.queryParam("jwt", "");
            redirectUrlComponentsBuilder.queryParam("ltiSuccessLoginRequired", user.getLogin());
        }

        String redirectUrl = redirectUrlComponentsBuilder.build().toString();
        log.info("redirect to url: {}", redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    /**
     * POST lti13/auth-callback Redirects an LTI 1.3 Authorization Request Response to the client
     *
     * @param request       HTTP request
     * @param response      HTTP response
     * @throws IOException If an input or output exception occurs
     */
    @PostMapping(value = "/lti13/auth-callback", produces = MediaType.APPLICATION_JSON_VALUE)
    public void lti13LaunchRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String state = request.getParameter("state");
        if (state == null) {
            errorOnMissingParameter(response, "state");
            return;
        }

        String idToken = request.getParameter("id_token");
        if (idToken == null) {
            errorOnMissingParameter(response, "id_token");
            return;
        }

        String redirectUri = LOGIN_REDIRECT_CLIENT_PATH + "?state=" + state + "&id_token=" + idToken;
        response.sendRedirect(redirectUri);
    }

    private void errorOnMissingParameter(HttpServletResponse response, String missingParamName) throws IOException {
        String message = "Missing parameter on oauth2 authorization response: " + missingParamName;
        log.error(message);
        response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), message);
    }

    /**
     * GET lti/configuration/:exerciseId : Generates LTI configuration parameters for an exercise.
     *
     * @param exerciseId the id of the exercise for the wanted LTI configuration
     * @param request    HTTP request
     * @return the ResponseEntity with status 200 (OK) and with body the LTI configuration, or with status 404 (Not Found)
     */
    @GetMapping(value = "lti/configuration/{exerciseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ExerciseLtiConfigurationDTO> exerciseLtiConfiguration(@PathVariable("exerciseId") Long exerciseId, HttpServletRequest request) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        OnlineCourseConfiguration ocConfiguration = course.getOnlineCourseConfiguration();
        if (ocConfiguration == null) {
            throw new BadRequestAlertException("LTI is not configured for this course", "LTI", "ltiNotConfigured");
        }

        String launchUrl = request.getScheme() + // "https"
                "://" +                                // "://"
                request.getServerName() +              // "myhost" // ":"
                (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "") + "/api/lti/launch/" + exercise.getId();

        return new ResponseEntity<>(new ExerciseLtiConfigurationDTO(launchUrl, ocConfiguration.getLtiKey(), ocConfiguration.getLtiSecret()), HttpStatus.OK);
    }

    /**
     * Gets the username for the LTI user prefixed with the configured user prefix
     *
     * @param launchRequest             the LTI launch request
     * @param onlineCourseConfiguration the configuration for the online course
     * @return the username for the LTI user
     */
    @NotNull
    private String createUsernameFromLaunchRequest(LtiLaunchRequestDTO launchRequest, OnlineCourseConfiguration onlineCourseConfiguration) {
        String username;

        if (!StringUtils.isEmpty(launchRequest.getExt_user_username())) {
            username = launchRequest.getExt_user_username();
        }
        else if (!StringUtils.isEmpty(launchRequest.getLis_person_sourcedid())) {
            username = launchRequest.getLis_person_sourcedid();
        }
        else if (!StringUtils.isEmpty(launchRequest.getUser_id())) {
            username = launchRequest.getUser_id();
        }
        else {
            String userEmail = launchRequest.getLis_person_contact_email_primary();
            username = userEmail.substring(0, userEmail.indexOf('@')); // Get the initial part of the user's email
        }

        return onlineCourseConfiguration.getUserPrefix() + "_" + username;
    }

    /**
     * Gets the first name for the user based on the LTI launch request
     *
     * @param launchRequest the LTI launch request
     * @return the first name for the LTI user
     */
    private String getUserFirstNameFromLaunchRequest(LtiLaunchRequestDTO launchRequest) {
        return launchRequest.getLis_person_name_given() != null ? launchRequest.getLis_person_name_given() : "";
    }

    /**
     * Gets the last name for the user considering the requests sent by the different LTI consumers
     *
     * @param launchRequest the LTI launch request
     * @return the last name for the LTI user
     */
    private String getUserLastNameFromLaunchRequest(LtiLaunchRequestDTO launchRequest) {
        if (!StringUtils.isEmpty(launchRequest.getLis_person_name_family())) {
            return launchRequest.getLis_person_name_family();
        }
        else if (!StringUtils.isEmpty(launchRequest.getLis_person_sourcedid())) {
            return launchRequest.getLis_person_sourcedid();
        }
        return "";
    }
}
