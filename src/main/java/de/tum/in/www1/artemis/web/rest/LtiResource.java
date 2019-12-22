package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseLtiConfigurationDTO;
import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;
import de.tum.in.www1.artemis.web.rest.util.ResponseUtil;

/**
 * Created by Josias Montag on 22.09.16.
 * <p>
 * REST controller for receiving LTI messages.
 */
@RestController
@RequestMapping("/api")
public class LtiResource {

    private final Logger log = LoggerFactory.getLogger(LtiResource.class);

    @Value("${artemis.lti.id}")
    private String LTI_ID;

    @Value("${artemis.lti.oauth-key}")
    private String LTI_OAUTH_KEY;

    @Value("${artemis.lti.oauth-secret}")
    private String LTI_OAUTH_SECRET;

    private final LtiService ltiService;

    private final UserService userService;

    private final ExerciseRepository exerciseRepository;

    private final TokenProvider tokenProvider;

    public LtiResource(LtiService ltiService, UserService userService, ExerciseRepository exerciseRepository, TokenProvider tokenProvider) {
        this.ltiService = ltiService;
        this.userService = userService;
        this.exerciseRepository = exerciseRepository;
        this.tokenProvider = tokenProvider;
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

        log.debug("Launch request : {}", launchRequest);

        // Verify request
        if (!ltiService.verifyRequest(request)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bad or expired request. Please try again.");
            return;
        }

        // Check if exercise ID is valid
        Optional<Exercise> optionalExercise = exerciseRepository.findById(exerciseId);
        if (optionalExercise.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Exercise not found");
            return;
        }

        Exercise exercise = optionalExercise.get();
        // Handle the launch request using LtiService
        try {
            ltiService.handleLaunchRequest(launchRequest, exercise);
        }
        catch (Exception ex) {
            log.error("Error during LIT launch request of exercise " + exercise.getTitle() + " for launch request: " + launchRequest + "\nError: " + ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
            return;
        }

        // If the current user was created within the last 15 seconds, we just created the user
        // Display a welcome message to the user
        boolean isNewUser = SecurityUtils.isAuthenticated()
                && TimeUnit.SECONDS.toMinutes(ZonedDateTime.now().toEpochSecond() - userService.getUser().getCreatedDate().toEpochMilli() * 1000) < 15;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String jwt = tokenProvider.createToken(authentication, true);

        // Note: The following redirect URL has to match the URL in user-route-access-service.ts in the method canActivate(...)

        String redirectUrl = request.getScheme() + // "https"
                "://" +                                // "://"
                request.getServerName() +              // "artemis.ase.in.tum.de"
                (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "") + "/#/overview/" + exercise.getCourse().getId()
                + "/exercises/" + exercise.getId() + (isNewUser ? "?welcome" : "") + (!SecurityUtils.isAuthenticated() ? "?login" : "")
                + (isNewUser || !SecurityUtils.isAuthenticated() ? "&" : "") + "jwt=" + jwt;

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

        return exercise.map(result -> {
            String launchUrl = request.getScheme() + // "http"
            "://" +                                // "://"
            request.getServerName() +              // "myhost" // ":"
            (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "") + "/api/lti/launch/" + exercise.get().getId();

            String ltiId = LTI_ID;
            String ltiPassport = LTI_ID + ":" + LTI_OAUTH_KEY + ":" + LTI_OAUTH_SECRET;
            return new ResponseEntity<>(new ExerciseLtiConfigurationDTO(launchUrl, ltiId, ltiPassport), HttpStatus.OK);
        }).orElse(ResponseUtil.notFound());
    }

}
