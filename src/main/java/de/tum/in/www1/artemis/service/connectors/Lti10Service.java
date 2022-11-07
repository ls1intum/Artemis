package de.tum.in.www1.artemis.service.connectors;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.imsglobal.lti.launch.LtiOauthVerifier;
import org.imsglobal.lti.launch.LtiVerificationException;
import org.imsglobal.lti.launch.LtiVerificationResult;
import org.imsglobal.lti.launch.LtiVerifier;
import org.imsglobal.pox.IMSPOXRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.util.StringUtils;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LtiOutcomeUrlRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;
import oauth.signpost.exception.OAuthException;

@Service
public class Lti10Service {

    private final Logger log = LoggerFactory.getLogger(Lti10Service.class);

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final LtiService ltiService;

    private final LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    private final ResultRepository resultRepository;

    private final HttpClient client;

    public Lti10Service(UserRepository userRepository, LtiOutcomeUrlRepository ltiOutcomeUrlRepository, ResultRepository resultRepository, CourseRepository courseRepository,
            LtiService ltiService) {
        this.userRepository = userRepository;
        this.ltiOutcomeUrlRepository = ltiOutcomeUrlRepository;
        this.resultRepository = resultRepository;
        this.courseRepository = courseRepository;
        this.ltiService = ltiService;
        this.client = HttpClientBuilder.create().build();
    }

    /**
     * Checks if an LTI request is correctly signed via OAuth with the secret
     *
     * @param request The request to check
     * @param onlineCourseConfiguration The configuration containing the secret used to verify the request
     * @return null if the request is valid, otherwise an error message which indicates the reason why the verification failed
     */
    public String verifyRequest(HttpServletRequest request, OnlineCourseConfiguration onlineCourseConfiguration) {
        if (onlineCourseConfiguration == null) {
            String message = "verifyRequest for LTI is not supported for this course";
            log.warn(message);
            return message;
        }

        LtiVerifier ltiVerifier = new LtiOauthVerifier();
        try {
            LtiVerificationResult ltiResult = ltiVerifier.verify(request, onlineCourseConfiguration.getLtiSecret());
            if (!ltiResult.getSuccess()) {
                final var message = "LTI signature verification failed with message: " + ltiResult.getMessage() + "; error: " + ltiResult.getError() + ", launch result: "
                        + ltiResult.getLtiLaunchResult();
                log.error(message);
                return message;
            }
        }
        catch (LtiVerificationException e) {
            log.error("Lti signature verification failed. ", e);
            return "Lti signature verification failed; " + e.getMessage();
        }
        // this is the success case
        log.info("LTI Oauth Request Verification successful");
        return null;
    }

    /**
     * Performs an LTI 1.0 exercise launch with the LTI parameters contained in launchRequest.
     * If the launch was successful the user is added to the target exercise group (e.g. the course).
     *
     * @param launchRequest the launch request
     * @param exercise the target exercise for the launch request
     * @param onlineCourseConfiguration the online configuration for the course the exercise belongs to
     */
    public void performLaunch(LtiLaunchRequestDTO launchRequest, Exercise exercise, OnlineCourseConfiguration onlineCourseConfiguration) {
        String username = createUsernameFromLaunchRequest(launchRequest, onlineCourseConfiguration);
        String firstName = getUserFirstNameFromLaunchRequest(launchRequest);
        String lastName = getUserLastNameFromLaunchRequest(launchRequest);
        ltiService.authenticateLtiUser(launchRequest.getLis_person_contact_email_primary(), launchRequest.getUser_id(), username, firstName, lastName,
                launchRequest.getCustom_require_existing_user(), launchRequest.getCustom_lookup_user_by_email());
        User user = userRepository.getUserWithGroupsAndAuthorities();
        ltiService.onSuccessfulLtiAuthentication(user, launchRequest.getUser_id(), exercise);
        saveLtiOutcomeUrl(user, exercise, launchRequest.getLis_outcome_service_url(), launchRequest.getLis_result_sourcedid());
    }

    /**
     * Gets the username for the LTI user prefixed with the configured user prefix
     *
     * @param launchRequest             the LTI launch request
     * @param onlineCourseConfiguration the configuration for the online course
     * @return the username for the LTI user
     */
    @NotNull
    protected String createUsernameFromLaunchRequest(LtiLaunchRequestDTO launchRequest, OnlineCourseConfiguration onlineCourseConfiguration) {
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
    protected String getUserFirstNameFromLaunchRequest(LtiLaunchRequestDTO launchRequest) {
        return launchRequest.getLis_person_name_given() != null ? launchRequest.getLis_person_name_given() : "";
    }

    /**
     * Gets the last name for the user considering the requests sent by the different LTI consumers
     *
     * @param launchRequest the LTI launch request
     * @return the last name for the LTI user
     */
    protected String getUserLastNameFromLaunchRequest(LtiLaunchRequestDTO launchRequest) {
        if (!StringUtils.isEmpty(launchRequest.getLis_person_name_family())) {
            return launchRequest.getLis_person_name_family();
        }
        else if (!StringUtils.isEmpty(launchRequest.getLis_person_sourcedid())) {
            return launchRequest.getLis_person_sourcedid();
        }
        return "";
    }

    /**
     * Save the LTI outcome url
     *
     * @param user      the user for which the lti outcome url should be saved
     * @param exercise  the exercise
     * @param url       the service url given by the LTI request
     * @param sourcedId the sourcedId given by the LTI request
     */
    private void saveLtiOutcomeUrl(User user, Exercise exercise, String url, String sourcedId) {

        if (url == null || url.isEmpty()) {
            return;
        }

        LtiOutcomeUrl ltiOutcomeUrl = ltiOutcomeUrlRepository.findByUserAndExercise(user, exercise).orElseGet(() -> {
            LtiOutcomeUrl newLtiOutcomeUrl = new LtiOutcomeUrl();
            newLtiOutcomeUrl.setUser(user);
            newLtiOutcomeUrl.setExercise(exercise);
            return newLtiOutcomeUrl;
        });
        ltiOutcomeUrl.setUrl(url);
        ltiOutcomeUrl.setSourcedId(sourcedId);
        ltiOutcomeUrlRepository.save(ltiOutcomeUrl);
    }

    /**
     * Adds the necessary query params for an LTI launch.
     *
     * @param uriComponentsBuilder the uri builder to add the query params to
     */
    public void addLtiQueryParams(UriComponentsBuilder uriComponentsBuilder) {
        ltiService.addLtiQueryParams(uriComponentsBuilder);
    }

    /**
     * This method is pinged on new exercise results. It sends a message to the LTI consumer with the new score.
     *
     * @param participation The exercise participation for which a new build result is available
     */
    public void onNewResult(StudentParticipation participation) {
        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId());
        OnlineCourseConfiguration onlineCourseConfiguration = course.getOnlineCourseConfiguration();

        if (onlineCourseConfiguration == null) {
            throw new IllegalStateException("Online course should have an online course configuration.");
        }

        // Get the LTI outcome URL
        participation.getStudents().forEach(student -> ltiOutcomeUrlRepository.findByUserAndExercise(student, participation.getExercise()).ifPresent(ltiOutcomeUrl -> {

            String score = "0.00";

            // Get the latest result
            Optional<Result> latestResult = resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participation.getId());

            if (latestResult.isPresent() && latestResult.get().getScore() != null) {
                // LTI scores needs to be formatted as String between "0.00" and "1.00"
                score = String.format(Locale.ROOT, "%.2f", latestResult.get().getScore().floatValue() / 100);
            }

            try {
                log.info("Reporting score {} for participation {} to LTI consumer with outcome URL {} using the source id {}", score, participation, ltiOutcomeUrl.getUrl(),
                        ltiOutcomeUrl.getSourcedId());
                HttpPost request = IMSPOXRequest.buildReplaceResult(ltiOutcomeUrl.getUrl(), onlineCourseConfiguration.getLtiKey(), onlineCourseConfiguration.getLtiSecret(),
                        ltiOutcomeUrl.getSourcedId(), score, null, false);
                HttpResponse response = client.execute(request);
                String responseString = new BasicResponseHandler().handleResponse(response);
                log.info("Response from LTI consumer: {}", responseString);
            }
            catch (HttpClientErrorException | IOException | OAuthException | GeneralSecurityException ex) {
                log.error("Reporting to LTI consumer failed", ex);
            }
        }));
    }
}
