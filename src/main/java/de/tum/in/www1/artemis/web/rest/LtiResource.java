package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.domain.User;
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

    private final TokenProvider tokenProvider;

    private final AuthorizationCheckService authCheckService;

    public LtiResource(LtiService ltiService, UserRepository userRepository, ExerciseRepository exerciseRepository, TokenProvider tokenProvider,
            AuthorizationCheckService authCheckService) {
        this.ltiService = ltiService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
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

        OnlineCourseConfiguration onlineCourseConfiguration = exercise.getCourseViaExerciseGroupOrCourseMember().getOnlineCourseConfiguration();
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
            ltiService.handleLaunchRequest(launchRequest, exercise, onlineCourseConfiguration);
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

        OnlineCourseConfiguration ocConfiguration = exercise.getCourseViaExerciseGroupOrCourseMember().getOnlineCourseConfiguration();
        if (ocConfiguration == null) {
            throw new BadRequestAlertException("LTI is not configured for this course", "LTI", "ltiNotConfigured");
        }

        String launchUrl = request.getScheme() + // "https"
                "://" +                                // "://"
                request.getServerName() +              // "myhost" // ":"
                (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "") + "/api/lti/launch/" + exercise.getId();

        return new ResponseEntity<>(new ExerciseLtiConfigurationDTO(launchUrl, ocConfiguration.getLtiKey(), ocConfiguration.getLtiSecret()), HttpStatus.OK);
    }
}
