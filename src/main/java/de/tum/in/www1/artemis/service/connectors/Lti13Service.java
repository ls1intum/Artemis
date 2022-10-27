package de.tum.in.www1.artemis.service.connectors;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.util.StringUtils;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lti.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.lti.Lti13TokenRetriever;
import de.tum.in.www1.artemis.service.OnlineCourseConfigurationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import net.minidev.json.JSONObject;

@Service
public class Lti13Service {

    private static final String EXERCISE_PATH_PATTERN = "/courses/{courseId}/exercises/{exerciseId}";

    private final Logger log = LoggerFactory.getLogger(Lti13Service.class);

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final Lti13ResourceLaunchRepository launchRepository;

    private final LtiService ltiService;

    private final ResultRepository resultRepository;

    private final Lti13TokenRetriever tokenRetriever;

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

    private final RestTemplate restTemplate;

    public Lti13Service(UserRepository userRepository, ExerciseRepository exerciseRepository, CourseRepository courseRepository, Lti13ResourceLaunchRepository launchRepository,
            LtiService ltiService, ResultRepository resultRepository, Lti13TokenRetriever tokenRetriever, OnlineCourseConfigurationService onlineCourseConfigurationService,
            RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
        this.ltiService = ltiService;
        this.launchRepository = launchRepository;
        this.resultRepository = resultRepository;
        this.tokenRetriever = tokenRetriever;
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.restTemplate = restTemplate;
    }

    /**
     * Performs an LTI 1.3 exercise launch with the LTI parameters contained in launchRequest.
     * If the launch was successful the user is added to the target exercise group (e.g. the course).
     *
     * @param ltiIdToken the id token for the user launching the request
     * @param clientRegistrationId the clientRegistrationId of the source LMS
     */
    public void performLaunch(OidcIdToken ltiIdToken, String clientRegistrationId) {

        String targetLinkUrl = ltiIdToken.getClaim(Claims.TARGET_LINK_URI);
        Optional<Exercise> targetExercise = getExerciseFromTargetLink(targetLinkUrl);
        if (targetExercise.isEmpty()) {
            String message = "No exercise to launch at " + targetLinkUrl;
            log.error(message);
            throw new BadRequestAlertException("Exercise not found", "LTI", "ltiExerciseNotFound");
        }
        Exercise exercise = targetExercise.get();

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        if (!course.getId().equals(exercise.getCourseViaExerciseGroupOrCourseMember().getId())) {
            String message = "Exercise is not related to course for target link url: " + targetLinkUrl;
            log.error(message);
            throw new BadRequestAlertException("Course not found", "LTI", "ltiCourseNotFound");
        }

        OnlineCourseConfiguration onlineCourseConfiguration = course.getOnlineCourseConfiguration();
        if (onlineCourseConfiguration == null) {
            String message = "Exercise is not related to course for target link url: " + targetLinkUrl;
            log.error(message);
            throw new BadRequestAlertException("LTI is not configured for this course", "LTI", "ltiNotConfigured");
        }

        ltiService.authenticateLtiUser(ltiIdToken.getEmail(), ltiIdToken.getSubject(), createUsernameFromLaunchRequest(ltiIdToken, onlineCourseConfiguration),
                ltiIdToken.getGivenName(), ltiIdToken.getFamilyName(), false, true);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        ltiService.onSuccessfulLtiAuthentication(user, ltiIdToken.getSubject(), targetExercise.get());

        Lti13LaunchRequest launchRequest = launchRequestFrom(ltiIdToken, clientRegistrationId);

        createOrUpdateResourceLaunch(launchRequest, user, targetExercise.get());
    }

    /**
     * Gets the username for the LTI user prefixed with the configured user prefix
     *
     * @param ltiIdToken             the token holding the launch information
     * @param onlineCourseConfiguration the configuration for the online course
     * @return the username for the LTI user
     */
    @NotNull
    private String createUsernameFromLaunchRequest(OidcIdToken ltiIdToken, OnlineCourseConfiguration onlineCourseConfiguration) {
        String username;

        if (!StringUtils.isEmpty(ltiIdToken.getPreferredUsername())) {
            username = ltiIdToken.getPreferredUsername();
        }
        else if (!StringUtils.isEmpty(ltiIdToken.getGivenName()) && !StringUtils.isEmpty(ltiIdToken.getFamilyName())) {
            username = ltiIdToken.getGivenName() + ltiIdToken.getFamilyName();
        }
        else {
            String userEmail = ltiIdToken.getEmail();
            username = userEmail.substring(0, userEmail.indexOf('@')); // Get the initial part of the user's email
        }
        username = username.replace(" ", "");

        return onlineCourseConfiguration.getUserPrefix() + "_" + username;
    }

    private Lti13LaunchRequest launchRequestFrom(OidcIdToken ltiIdToken, String clientRegistrationId) {
        try {
            return new Lti13LaunchRequest(ltiIdToken, clientRegistrationId);
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Could not create LTI 1.3 launch request with provided idToken: " + ex.getMessage());
        }
    }

    /**
     * This method is pinged on new exercise results. It sends a message to the related LTI 1.3 platforms with the new score.
     *
     * @param participation The exercise participation for which a new result is available
     */
    public void onNewResult(StudentParticipation participation) {
        var students = participation.getStudents();

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId());
        ClientRegistration clientRegistration = onlineCourseConfigurationService.getClientRegistration(course.getOnlineCourseConfiguration());
        if (clientRegistration == null) {
            log.error("Could not transmit score to external LMS for course {}: client registration not found", course.getTitle());
            return;
        }

        if (students != null) {
            students.forEach(student -> {
                // there can be multiple launches for one exercise and student if the student has used more than one LTI 1.3 platform
                // to launch the exercise (for example multiple lms)
                Collection<LtiResourceLaunch> launches = launchRepository.findByUserAndExercise(student, participation.getExercise());

                if (launches.isEmpty()) {
                    return;
                }

                Optional<Result> result = resultRepository.findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(participation.getId());

                if (result.isEmpty()) {
                    log.error("onNewResult triggered for participation " + participation.getId() + " but no result could be found");
                    return;
                }

                String concatenatedFeedbacks = result.get().getFeedbacks().stream().map(Feedback::getDetailText).collect(Collectors.joining(". "));

                launches.forEach(launch -> submitScore(launch, clientRegistration, concatenatedFeedbacks, result.get().getScore()));
            });
        }
    }

    protected void submitScore(LtiResourceLaunch launch, ClientRegistration clientRegistration, String comment, Double score) {
        String scoreLineItemUrl = launch.getScoreLineItemUrl();
        if (scoreLineItemUrl == null) {
            return;
        }

        String token = tokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        if (token == null) {
            log.error("Could not transmit score to " + clientRegistration.getClientId() + ": missing token");
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/vnd.ims.lis.v1.score+json"));
        headers.setBearerAuth(token);
        String body = getScoreBody(launch.getSub(), comment, score);
        HttpEntity<String> httpRequest = new HttpEntity<>(body, headers);
        try {
            restTemplate.postForEntity(getScoresUrl(launch.getScoreLineItemUrl()), httpRequest, Object.class);
            log.info("Submitted score for " + launch.getUser().getLogin() + " to client" + clientRegistration.getClientId());
        }
        catch (HttpClientErrorException e) {
            String message = "Could not submit score for " + launch.getUser().getLogin() + " to client " + clientRegistration.getClientId() + ": " + e.getMessage();
            log.error(message);
        }
    }

    private String getScoresUrl(String lineItemUrl) {
        StringBuilder builder = new StringBuilder(lineItemUrl);
        int index = lineItemUrl.indexOf("?");
        if (index == -1) {
            return builder.append("/scores").toString();
        }
        return builder.insert(index, "/scores").toString(); // Adds "/scores" before the "?" in case there are query parameters
    }

    private String getScoreBody(String userId, String comment, Double score) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("userId", userId);
        requestBody.put("timestamp", (new DateTime()).toString());
        requestBody.put("activityProgress", "Submitted");
        requestBody.put("gradingProgress", "FullyGraded");
        requestBody.put("comment", comment);
        requestBody.put("scoreGiven", score);
        requestBody.put("scoreMaximum", 100D);
        return requestBody.toJSONString();
    }

    /**
     * Returns an Optional of an Exercise that was referenced by targetLinkUrl.
     *
     * @param targetLinkUrl to retrieve an Exercise
     * @return the Exercise or nothing otherwise
     */
    private Optional<Exercise> getExerciseFromTargetLink(String targetLinkUrl) {
        AntPathMatcher matcher = new AntPathMatcher();

        String targetLinkPath;
        try {
            targetLinkPath = (new URL(targetLinkUrl)).getPath();
        }
        catch (MalformedURLException ex) {
            log.info("Malformed target link url: " + targetLinkUrl);
            return Optional.empty();
        }

        if (!matcher.match(EXERCISE_PATH_PATTERN, targetLinkPath)) {
            log.info("Could not extract exerciseId and courseId from target link: " + targetLinkUrl);
            return Optional.empty();
        }
        Map<String, String> pathVariables = matcher.extractUriTemplateVariables(EXERCISE_PATH_PATTERN, targetLinkPath);

        String exerciseId = pathVariables.get("exerciseId");

        Optional<Exercise> exerciseOpt = exerciseRepository.findById(Long.valueOf(exerciseId));

        if (exerciseOpt.isEmpty()) {
            log.info("Could not find exercise or course for target link url: " + targetLinkUrl);
            return Optional.empty();
        }

        return exerciseOpt;
    }

    private void createOrUpdateResourceLaunch(Lti13LaunchRequest launchRequest, User user, Exercise exercise) {
        Optional<LtiResourceLaunch> launchOpt = launchRepository.findByIssAndSubAndDeploymentIdAndResourceLinkId(launchRequest.getIss(), launchRequest.getSub(),
                launchRequest.getDeploymentId(), launchRequest.getResourceLinkId());

        LtiResourceLaunch launch = launchOpt.orElse(LtiResourceLaunch.from(launchRequest));

        Lti13AgsClaim agsClaim = launchRequest.getAgsClaim();
        // we do support LTI 1.3 Assigment and Grading Services SCORE publish service
        if (agsClaim != null) {
            launch.setScoreLineItemUrl(agsClaim.getLineItem());
        }

        launch.setExercise(exercise);
        launch.setUser(user);
        launchRepository.save(launch);
    }

    /**
     * Adds the necessary query params for an LTI launch.
     *
     * @param uriComponentsBuilder the uri builder to add the query params to
     */
    public void addLtiQueryParams(UriComponentsBuilder uriComponentsBuilder) {
        ltiService.addLtiQueryParams(uriComponentsBuilder);
    }
}
