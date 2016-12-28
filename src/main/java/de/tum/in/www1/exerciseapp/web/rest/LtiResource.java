package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.repository.ExerciseRepository;
import de.tum.in.www1.exerciseapp.security.SecurityUtils;
import de.tum.in.www1.exerciseapp.service.LtiService;
import de.tum.in.www1.exerciseapp.service.UserService;
import de.tum.in.www1.exerciseapp.web.rest.dto.ExerciseLtiConfigurationDTO;
import de.tum.in.www1.exerciseapp.web.rest.dto.LtiLaunchRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Created by Josias Montag on 22.09.16.
 * <p>
 * REST controller for receiving LTI messages.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
public class LtiResource {

    private final Logger log = LoggerFactory.getLogger(LtiResource.class);


    @Value("${exerciseapp.lti.id}")
    private String LTI_ID;

    @Value("${exerciseapp.lti.oauth-key}")
    private String LTI_OAUTH_KEY;

    @Value("${exerciseapp.lti.oauth-secret}")
    private String LTI_OAUTH_SECRET;

    @Inject
    private LtiService ltiService;

    @Inject
    private UserService userService;

    @Inject
    private ExerciseRepository exerciseRepository;


    /**
     * POST  /lti/launch/:exerciseId : Launch the exercise app using request by an LTI consumer. Redirects the user to the exercise on success.
     *
     * @param launchRequest the LTI laucnh request (ExerciseLtiConfigurationDTO)
     * @param exerciseId    the id of the exercise the user wants to open
     * @param request       HTTP request
     * @param response      HTTP response
     */
    @RequestMapping(value = "/lti/launch/{exerciseId}",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public void launch(@ModelAttribute LtiLaunchRequestDTO launchRequest, @PathVariable("exerciseId") Long exerciseId, HttpServletRequest request, HttpServletResponse response) throws IOException {

        log.debug("Launch request : {}", launchRequest);


        if (!ltiService.verifyRequest(request)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bad or expired request. Please try again.");
            return;
        }

        Exercise exercise = exerciseRepository.findOne(exerciseId);
        if (exercise == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Exercise not found");
            return;
        }

        try {
            ltiService.handleLaunchRequest(launchRequest, exercise);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            return;
        }

        Boolean isNewUser = SecurityUtils.isAuthenticated() && TimeUnit.SECONDS.toMinutes(ZonedDateTime.now().toEpochSecond() - userService.getUser().getCreatedDate().toEpochSecond()) < 15;

        String redirectUrl = request.getScheme() + // "http"
            "://" +                                // "://"
            request.getServerName() +              // "myhost"
            (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "" ) +
            "/#/courses/" + exercise.getCourse().getId() + "/exercise/" + exercise.getId() +
            (isNewUser ? "?welcome" : "") +
            (!SecurityUtils.isAuthenticated() ? "?login" : "");



        response.sendRedirect(redirectUrl);


    }


    /**
     * GET  /lti/configuration/:exerciseId : Generates LTI configuration parameters for an exercise.
     *
     * @param exerciseId the id of the exercise for the wanted LTI configuration
     * @param request    HTTP request
     * @return the ResponseEntity with status 200 (OK) and with body the LTI configuration, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/lti/configuration/{exerciseId}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExerciseLtiConfigurationDTO> exerciseLtiConfiguration(@PathVariable("exerciseId") Long exerciseId, HttpServletRequest request) {
        Exercise exercise = exerciseRepository.findOne(exerciseId);


        return Optional.ofNullable(exercise)
            .map(result -> {
                String launchUrl = request.getScheme() + // "http"
                    "://" +                                // "://"
                    request.getServerName() +              // "myhost"                     // ":"
                    (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "" ) +
                    "/api/lti/launch/" + exercise.getId();

                String ltiId = LTI_ID;
                String ltiPassport = LTI_ID + ":" + LTI_OAUTH_KEY + ":" + LTI_OAUTH_SECRET;
                return new ResponseEntity<>(new ExerciseLtiConfigurationDTO(launchUrl, ltiId, ltiPassport), HttpStatus.OK);
            })
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }


}
