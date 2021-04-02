package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseLtiConfigurationDTO;
import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;

/**
 * Created by Josias Montag on 22.09.16.
 * <p>
 * REST controller for receiving LTI messages.
 */
@RestController
@RequestMapping("/api")
public class LtiResource {

    private final Logger log = LoggerFactory.getLogger(LtiResource.class);

    @Value("${artemis.lti.id:#{null}}")
    private Optional<String> LTI_ID;

    @Value("${artemis.lti.oauth-key:#{null}}")
    private Optional<String> LTI_OAUTH_KEY;

    @Value("${artemis.lti.oauth-secret:#{null}}")
    private Optional<String> LTI_OAUTH_SECRET;

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
     * POST /lti/launch/:exerciseId : Launch the exercise app using request by a LTI consumer. Redirects the user to the exercise on success.
     *
     * @param launchRequest the LTI launch request (ExerciseLtiConfigurationDTO)
     * @param exerciseId    the id of the exercise the user wants to open
     * @param request       HTTP request
     * @param response      HTTP response
     * @throws IOException  If an input or output exception occurs
     */
    @PostMapping(value = "/lti/launch/{exerciseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public void launch(@ModelAttribute LtiLaunchRequestDTO launchRequest, @PathVariable("exerciseId") Long exerciseId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        log.info("/lti/launch/" + exerciseId + " with launch request: " + launchRequest);

        if (this.LTI_OAUTH_SECRET.isEmpty() || this.LTI_ID.isEmpty() || this.LTI_OAUTH_KEY.isEmpty()) {
            String message = "LTI not configured on this Artemis server. Cannot launch exercise " + exerciseId + ". " + "Please contact an admin or try again.";
            log.warn(message);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, message);
        }

        log.info("Request header X-Forwarded-Proto: " + request.getHeader("X-Forwarded-Proto"));
        log.info("Request header X-Forwarded-For: " + request.getHeader("X-Forwarded-For"));

        if (!request.getRequestURL().toString().startsWith("https")) {
            log.error("The request url " + request.getRequestURL().toString() + " does not start with 'https'. Verification of the request will most probably fail."
                    + "Please double check your loadbalancer (e.g. nginx) configuration and your Spring configuration (e.g. application.yml) with respect to proxy_set_header "
                    + "X-Forwarded-Proto and forward-headers-strategy: native");
        }

        log.info("Try to verify LTI Oauth Request");

        // Verify request
        String error = ltiService.verifyRequest(request);
        if (error != null) {
            log.warn("Failed verification for launch request : {}", launchRequest);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, error + ". Cannot launch exercise " + exerciseId + ". " + "Please contact an admin or try again.");
            return;
        }

        log.info("Oauth Verification succeeded");

        // Check if exercise ID is valid
        Optional<Exercise> optionalExercise = exerciseRepository.findById(exerciseId);
        if (optionalExercise.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Exercise not found");
            return;
        }

        Exercise exercise = optionalExercise.get();
        log.info("found exercise " + exercise.getTitle());
        // Handle the launch request using LtiService
        try {
            ltiService.handleLaunchRequest(launchRequest, exercise);
        }
        catch (Exception ex) {
            log.error("Error during LTI launch request of exercise " + exercise.getTitle() + " for launch request: " + launchRequest + "\nError: ", ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
            return;
        }

        log.info("handleLaunchRequest done");

        // If the current user was created within the last 15 seconds, we just created the user
        // Display a welcome message to the user
        boolean isNewUser = SecurityUtils.isAuthenticated()
                && TimeUnit.SECONDS.toMinutes(ZonedDateTime.now().toEpochSecond() - userRepository.getUser().getCreatedDate().toEpochMilli() * 1000) < 15;

        log.info("isNewUser: " + isNewUser);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String jwt = tokenProvider.createToken(authentication, true);

        log.info("created jwt token: " + jwt);

        // Note: The following redirect URL has to match the URL in user-route-access-service.ts in the method canActivate(...)

        String redirectUrl = request.getScheme() + // "https"
                "://" +                                // "://"
                request.getServerName() +              // "artemis.ase.in.tum.de"
                (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "") + "/courses/"
                + exercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/exercises/" + exercise.getId() + (isNewUser ? "?welcome" : "")
                + (!SecurityUtils.isAuthenticated() ? "?login" : "") + (isNewUser || !SecurityUtils.isAuthenticated() ? "&" : "") + "jwt=" + jwt;

        log.info("redirect to url: " + redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    /**
     * GET /lti/configuration/:exerciseId : Generates LTI configuration parameters for an exercise.
     *
     * @param exerciseId the id of the exercise for the wanted LTI configuration
     * @param request    HTTP request
     * @return the ResponseEntity with status 200 (OK) and with body the LTI configuration, or with status 404 (Not Found)
     */
    @GetMapping(value = "/lti/configuration/{exerciseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ExerciseLtiConfigurationDTO> exerciseLtiConfiguration(@PathVariable("exerciseId") Long exerciseId, HttpServletRequest request) {
        Optional<Exercise> exercise = exerciseRepository.findById(exerciseId);
        if (exercise.isEmpty()) {
            return notFound();
        }
        if (!authCheckService.isAtLeastInstructorForExercise(exercise.get(), null)) {
            return forbidden();
        }
        if (LTI_ID.isEmpty() || LTI_OAUTH_KEY.isEmpty() || LTI_OAUTH_SECRET.isEmpty()) {
            log.warn(
                    "lti/configuration is not supported on this Artemis instance, no artemis.lti.id, artemis.lti.oauth-key or artemis.lti.oauth-secret were specified in the yml configuration");
            return forbidden();
        }

        String launchUrl = request.getScheme() + // "https"
                "://" +                                // "://"
                request.getServerName() +              // "myhost" // ":"
                (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "") + "/api/lti/launch/" + exercise.get().getId();

        String ltiId = LTI_ID.get();
        String ltiPassport = ltiId + ":" + LTI_OAUTH_KEY.get() + ":" + LTI_OAUTH_SECRET.get();
        return new ResponseEntity<>(new ExerciseLtiConfigurationDTO(launchUrl, ltiId, ltiPassport), HttpStatus.OK);
    }
}
