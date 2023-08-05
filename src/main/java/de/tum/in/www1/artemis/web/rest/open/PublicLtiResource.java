package de.tum.in.www1.artemis.web.rest.open;

import java.io.IOException;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.glassfish.jersey.uri.UriComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.connectors.lti.Lti10Service;
import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;

/**
 * REST controller for receiving LTI requests.
 */
@RestController
@RequestMapping("api/public/")
public class PublicLtiResource {

    private final Logger log = LoggerFactory.getLogger(PublicLtiResource.class);

    private final Lti10Service lti10Service;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    public static final String LOGIN_REDIRECT_CLIENT_PATH = "/lti/launch";

    public PublicLtiResource(Lti10Service lti10Service, ExerciseRepository exerciseRepository, CourseRepository courseRepository) {
        this.lti10Service = lti10Service;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * POST lti/launch/:exerciseId : Launch the exercise app using request by an LTI consumer. Redirects the user to the exercise on success.
     *
     * @param launchRequest the LTI launch request (ExerciseLtiConfigurationDTO)
     * @param exerciseId    the id of the exercise the user wants to open
     * @param request       HTTP request
     * @param response      HTTP response
     * @throws IOException If an input or output exception occurs
     */
    // TODO: We should extract some functionality to a service to simplify the method
    @PostMapping("lti/launch/{exerciseId}")
    @EnforceNothing
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
        String error = lti10Service.verifyRequest(request, onlineCourseConfiguration);
        if (error != null) {
            log.warn("Failed verification for launch request : {}", launchRequest);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, error + ". Cannot launch exercise " + exerciseId + ". " + "Please contact an admin or try again.");
            return;
        }
        log.debug("Oauth Verification succeeded");

        try {
            lti10Service.performLaunch(launchRequest, exercise, onlineCourseConfiguration);
        }
        catch (InternalAuthenticationServiceException ex) {
            log.error("Error during LTI launch request of exercise {} for launch request: {}", exercise.getTitle(), launchRequest, ex);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Cannot launch exercise " + exerciseId + ". " + "Please contact an admin or try again.");
            return;
        }
        catch (Exception ex) {
            log.error("Error during LTI launch request of exercise {} for launch request: {}", exercise.getTitle(), launchRequest, ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot launch exercise " + exerciseId + ". " + "Please contact an admin or try again.");
            return;
        }

        log.debug("handleLaunchRequest done");

        sendRedirect(request, response, exercise);
    }

    /**
     * POST lti13/auth-callback Redirects an LTI 1.3 Authorization Request Response to the client
     *
     * @param request  HTTP request
     * @param response HTTP response
     * @throws IOException If an input or output exception occurs
     */
    @PostMapping("lti13/auth-callback")
    @EnforceNothing
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

        if (!isValidJwtIgnoreSignature(idToken)) {
            errorOnIllegalParameter(response, "id_token");
            return;
        }

        UriComponentsBuilder uriBuilder = buildRedirect(request);
        uriBuilder.path(LOGIN_REDIRECT_CLIENT_PATH);
        uriBuilder.queryParam("state", UriComponent.encode(state, UriComponent.Type.QUERY_PARAM));
        uriBuilder.queryParam("id_token", UriComponent.encode(idToken, UriComponent.Type.QUERY_PARAM));
        String redirectUrl = uriBuilder.build().toString();
        log.info("redirect to url: {}", redirectUrl);
        response.sendRedirect(redirectUrl); // Redirect using user-provided values is safe because user-provided values are used in the query parameters, not the url itself
    }

    /**
     * Strips the signature from a potential JWT and makes sure the rest is valid.
     *
     * @param token The potential token
     * @return Whether the token is valid or not
     */
    private boolean isValidJwtIgnoreSignature(String token) {
        String strippedToken = token.substring(0, token.lastIndexOf(".") + 1);
        try {
            Jwts.parserBuilder().build().parse(strippedToken);
            return true;
        }
        catch (SignatureException e) {
            // We ignore the signature
            return true;
        }
        catch (ExpiredJwtException | MalformedJwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private void errorOnMissingParameter(HttpServletResponse response, String missingParamName) throws IOException {
        String message = "Missing parameter on oauth2 authorization response: " + missingParamName;
        log.error(message);
        response.sendError(HttpStatus.BAD_REQUEST.value(), message);
    }

    private void errorOnIllegalParameter(HttpServletResponse response, String missingParamName) throws IOException {
        String message = "Illegal parameter on oauth2 authorization response: " + missingParamName;
        log.error(message);
        response.sendError(HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * Redirects the launch request to Artemis.
     * Note: The following redirect URL has to match the URL in user-route-access-service.ts in the method canActivate(...)
     *
     * @param request  HTTP request
     * @param response HTTP response
     * @param exercise The exercise to redirect to
     * @throws IOException If an input or output exception occurs
     */
    private void sendRedirect(HttpServletRequest request, HttpServletResponse response, Exercise exercise) throws IOException {

        UriComponentsBuilder uriBuilder = buildRedirect(request);
        uriBuilder.pathSegment("courses") //
                .pathSegment(exercise.getCourseViaExerciseGroupOrCourseMember().getId().toString()) //
                .pathSegment("exercises") //
                .pathSegment(exercise.getId().toString()); //

        lti10Service.buildLtiResponse(uriBuilder, response);

        String redirectUrl = uriBuilder.build().toString();
        log.info("redirect to url: {}", redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    private UriComponentsBuilder buildRedirect(HttpServletRequest request) {
        UriComponentsBuilder redirectUrlComponentsBuilder = UriComponentsBuilder.newInstance().scheme(request.getScheme()).host(request.getServerName());
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            redirectUrlComponentsBuilder.port(request.getServerPort());
        }
        return redirectUrlComponentsBuilder;
    }
}
