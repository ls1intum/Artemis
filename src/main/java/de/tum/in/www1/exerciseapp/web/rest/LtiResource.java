package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.repository.ExerciseRepository;
import de.tum.in.www1.exerciseapp.service.LtiService;
import de.tum.in.www1.exerciseapp.web.rest.dto.LtiLaunchRequestDTO;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.imsglobal.lti.launch.LtiVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Created by Josias Montag on 22.09.16.
 * <p>
 * REST controller for receiving LTI messages.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
public class LtiResource {

    private final Logger log = LoggerFactory.getLogger(LtiResource.class);

    @Inject
    private LtiService ltiService;

    @Inject
    private ExerciseRepository exerciseRepository;


    /**
     * POST  /lti/launch/:exerciseId : Launch the exercise app using request by an LTI consumer
     *
     * @param exerciseId the id of the exercise the user wants to open
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
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

        String redirectUrl = request.getScheme() + // "http"
            "://" +                                // "://"
            request.getServerName() +              // "myhost"
            ":" +                                  // ":"
            request.getServerPort();


        response.sendRedirect(redirectUrl);


    }


}
