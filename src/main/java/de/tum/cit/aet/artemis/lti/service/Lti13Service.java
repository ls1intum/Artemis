package de.tum.cit.aet.artemis.lti.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.ArtemisAuthenticationProvider;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lti.config.Lti13TokenRetriever;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.domain.LtiResourceLaunch;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.lti.dto.Claims;
import de.tum.cit.aet.artemis.lti.dto.Lti13AgsClaim;
import de.tum.cit.aet.artemis.lti.dto.Lti13LaunchRequest;
import de.tum.cit.aet.artemis.lti.dto.Scopes;
import de.tum.cit.aet.artemis.lti.repository.Lti13ResourceLaunchRepository;
import de.tum.cit.aet.artemis.lti.repository.LtiPlatformConfigurationRepository;

@Lazy
@Service
@Profile(PROFILE_LTI)
public class Lti13Service {

    private static final String EXERCISE_PATH_PATTERN = "/courses/{courseId}/exercises/{exerciseId}";

    private static final String LECTURE_PATH_PATTERN = "/courses/{courseId}/lectures/{lectureId}";

    private static final String COMPETENCY_PATH_PATTERN = "/courses/{courseId}/competencies";

    private static final String IRIS_PATH_PATTERN = "/courses/{courseId}/dashboard";

    private static final String LEARNING_PATH_PATH_PATTERN = "/courses/{courseId}/learning-path";

    private static final String COURSE_PATH_PATTERN = "/courses/{courseId}/**";

    private static final Logger log = LoggerFactory.getLogger(Lti13Service.class);

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final CourseRepository courseRepository;

    private final Lti13ResourceLaunchRepository launchRepository;

    private final LtiService ltiService;

    private final ResultRepository resultRepository;

    private final Lti13TokenRetriever tokenRetriever;

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

    private final LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final RestTemplate restTemplate;

    private static final Map<String, DeepLinkingType> TARGET_LINK_PATTERNS = Map.of(COMPETENCY_PATH_PATTERN, DeepLinkingType.COMPETENCY, LEARNING_PATH_PATH_PATTERN,
            DeepLinkingType.LEARNING_PATH, IRIS_PATH_PATTERN, DeepLinkingType.IRIS, LECTURE_PATH_PATTERN, DeepLinkingType.LECTURE);

    public Lti13Service(UserRepository userRepository, ExerciseRepository exerciseRepository, Optional<LectureRepositoryApi> lectureRepositoryApi,
            CourseRepository courseRepository, Lti13ResourceLaunchRepository launchRepository, LtiService ltiService, ResultRepository resultRepository,
            Lti13TokenRetriever tokenRetriever, OnlineCourseConfigurationService onlineCourseConfigurationService, RestTemplate restTemplate,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository) {
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.courseRepository = courseRepository;
        this.ltiService = ltiService;
        this.launchRepository = launchRepository;
        this.resultRepository = resultRepository;
        this.tokenRetriever = tokenRetriever;
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.restTemplate = restTemplate;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.ltiPlatformConfigurationRepository = ltiPlatformConfigurationRepository;
    }

    /**
     * Performs an LTI 1.3 exercise launch with the LTI parameters contained in launchRequest.
     * If the launch was successful the user is added to the target exercise group (e.g. the course).
     *
     * @param ltiIdToken           the id token for the user launching the request
     * @param clientRegistrationId the clientRegistrationId of the source LMS
     */
    public void performLaunch(OidcIdToken ltiIdToken, String clientRegistrationId) {
        String targetLinkUrl = ltiIdToken.getClaim(Claims.TARGET_LINK_URI);
        Optional<Exercise> targetExercise = getExerciseFromTargetLink(targetLinkUrl);
        Optional<Lecture> targetLecture = getLectureFromTargetLink(targetLinkUrl);
        Optional<Course> targetCourse = getCourseFromTargetLink(targetLinkUrl);

        if (targetCourse.isEmpty()) {
            String message = "No course to launch at " + targetLinkUrl;
            log.error(message);
            throw new BadRequestAlertException("Course not found", "LTI", "ltiCourseNotFound");
        }

        Course course = targetCourse.get();
        OnlineCourseConfiguration onlineCourseConfiguration = courseRepository.findWithEagerOnlineCourseConfigurationById(course.getId()).getOnlineCourseConfiguration();
        if (onlineCourseConfiguration == null) {
            String message = "LTI is not configured for course with target link URL: " + targetLinkUrl;
            log.error(message);
            throw new BadRequestAlertException("LTI is not configured for this course", "LTI", "ltiNotConfigured");
        }

        Optional<String> optionalUsername = artemisAuthenticationProvider.getUsernameForEmail(ltiIdToken.getEmail())
                .or(() -> userRepository.findOneByEmailIgnoreCase(ltiIdToken.getEmail()).map(User::getLogin));

        if (!onlineCourseConfiguration.isRequireExistingUser() && optionalUsername.isEmpty()) {
            SecurityContextHolder.getContext().setAuthentication(ltiService.createNewUserFromLaunchRequest(ltiIdToken.getEmail(),
                    createUsernameFromLaunchRequest(ltiIdToken, onlineCourseConfiguration), ltiIdToken.getGivenName(), ltiIdToken.getFamilyName()));
        }

        String username = optionalUsername.orElseGet(() -> createUsernameFromLaunchRequest(ltiIdToken, onlineCourseConfiguration));
        User user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username).orElseThrow();
        Lti13LaunchRequest launchRequest = launchRequestFrom(ltiIdToken, clientRegistrationId);

        if (targetExercise.isPresent()) {
            Exercise exercise = targetExercise.get();
            handleLaunchRequest(launchRequest, user, exercise, ltiIdToken, onlineCourseConfiguration);
        }
        else if (hasTargetLinkWithoutExercise(targetLinkUrl, targetLecture)) {
            handleLaunchRequest(launchRequest, user, null, ltiIdToken, onlineCourseConfiguration);
        }
        else {
            log.error("No course content to launch at {}", targetLinkUrl);
            throw new BadRequestAlertException("Content not found", "LTI", "ltiContentNotFound");
        }
    }

    /**
     * Gets the username for the LTI user prefixed with the configured user prefix
     *
     * @param ltiIdToken                the token holding the launch information
     * @param onlineCourseConfiguration the configuration for the online course
     * @return the username for the LTI user
     */
    @NotNull
    public String createUsernameFromLaunchRequest(OidcIdToken ltiIdToken, OnlineCourseConfiguration onlineCourseConfiguration) {
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
            return Lti13LaunchRequest.from(ltiIdToken, clientRegistrationId);
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
        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId());

        if (!course.isOnlineCourse()) {
            log.error("Could not transmit score to external LMS for course {}:", course.getTitle());
            return;
        }

        participation.getStudents().forEach(student -> {
            // there can be multiple launches for one exercise and student if the student has used more than one LTI 1.3 platform
            // to launch the exercise (for example multiple lms)
            Collection<LtiResourceLaunch> launches = launchRepository.findByUserAndExercise(student, participation.getExercise());

            if (launches.isEmpty()) {
                return;
            }

            Optional<Result> result = resultRepository.findFirstWithSubmissionAndFeedbacksAndTestCasesByParticipationIdOrderByCompletionDateDesc(participation.getId());

            if (result.isEmpty()) {
                log.error("onNewResult triggered for participation {} but no result could be found", participation.getId());
                return;
            }

            String concatenatedFeedbacks = result.get().getFeedbacks().stream().map(Feedback::getDetailText).collect(Collectors.joining(". "));

            launches.forEach(launch -> {
                LtiPlatformConfiguration returnPlatform = launch.getLtiPlatformConfiguration();
                ClientRegistration returnClient = onlineCourseConfigurationService.getClientRegistration(returnPlatform);
                submitScore(launch, returnClient, concatenatedFeedbacks, result.get().getScore());

            });
        });
    }

    protected void submitScore(LtiResourceLaunch launch, ClientRegistration clientRegistration, String comment, Double score) {
        String scoreLineItemUrl = getScoresUrl(launch.getScoreLineItemUrl());
        if (scoreLineItemUrl == null) {
            return;
        }

        String token = tokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        if (token == null) {
            log.error("Could not transmit score to {}: missing token", clientRegistration.getClientId());
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/vnd.ims.lis.v1.score+json"));
            headers.setBearerAuth(token);
            String body = getScoreBody(launch.getSub(), comment, score);
            HttpEntity<String> httpRequest = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(scoreLineItemUrl, httpRequest, Object.class);
            log.info("Submitted score for {} to client {}", launch.getUser().getLogin(), clientRegistration.getClientId());
        }
        catch (HttpClientErrorException | JsonProcessingException e) {
            String message = "Could not submit score for " + launch.getUser().getLogin() + " to client " + clientRegistration.getClientId() + ": " + e.getMessage();
            log.error(message);
        }
    }

    private String getScoresUrl(String lineItemUrl) {
        if (StringUtils.isEmpty(lineItemUrl)) {
            return null;
        }
        StringBuilder builder = new StringBuilder(lineItemUrl);
        int index = lineItemUrl.indexOf("?");
        if (index == -1) {
            return builder.append("/scores").toString();
        }
        return builder.insert(index, "/scores").toString(); // Adds "/scores" before the "?" in case there are query parameters
    }

    private String getScoreBody(String userId, String comment, Double score) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("userId", userId);
        requestBody.put("timestamp", new DateTime().toString());
        requestBody.put("activityProgress", "Submitted");
        requestBody.put("gradingProgress", "FullyGraded");
        requestBody.put("comment", comment);
        requestBody.put("scoreGiven", score);
        requestBody.put("scoreMaximum", 100D);
        return new ObjectMapper().writeValueAsString(requestBody);
    }

    /**
     * Helper method to extract an entity (e.g., Exercise, Lecture, Course) from a target link URL.
     * This method handles the common logic of parsing the URL, matching the path pattern, extracting variables,
     * and fetching the entity from the repository.
     *
     * @param <T>              The type of entity to extract (e.g., Exercise, Lecture, Course).
     * @param targetLinkUrl    The target link URL to parse.
     * @param pathPattern      The path pattern to match against (e.g., EXERCISE_PATH_PATTERN).
     * @param repositoryFinder A function that takes the entity ID and returns an Optional<T> from the repository.
     * @param entityName       The name of the entity (e.g., "exercise", "lecture", "course") for logging purposes.
     * @return An Optional containing the entity if found, or an empty Optional otherwise.
     */
    private <T> Optional<T> extractEntityFromTargetLink(String targetLinkUrl, String pathPattern, Function<String, Optional<T>> repositoryFinder, String entityName) {
        AntPathMatcher matcher = new AntPathMatcher();
        String targetLinkPath;

        try {
            targetLinkPath = new URI(targetLinkUrl).getPath();
        }
        catch (URISyntaxException ex) {
            log.info("Malformed target link URL: {}", targetLinkUrl);
            return Optional.empty();
        }

        if (!matcher.match(pathPattern, targetLinkPath)) {
            log.info("Could not extract {} from target link: {}", entityName, targetLinkUrl);
            return Optional.empty();
        }

        Map<String, String> pathVariables = matcher.extractUriTemplateVariables(pathPattern, targetLinkPath);
        String entityId = pathVariables.get(entityName.toLowerCase() + "Id");

        try {
            return repositoryFinder.apply(entityId);
        }
        catch (Exception ex) {
            log.info("Invalid {} ID in target link URL: {}", entityName, targetLinkUrl);
            return Optional.empty();
        }
    }

    /**
     * Retrieves an Optional of an Exercise referenced by the given target link URL.
     * This method extracts the exercise ID from the URL using the pattern "/courses/{courseId}/exercises/{exerciseId}".
     *
     * @param targetLinkUrl The target link URL to retrieve an Exercise.
     * @return An Optional containing the Exercise if found, or an empty Optional otherwise.
     */
    private Optional<Exercise> getExerciseFromTargetLink(String targetLinkUrl) {
        return extractEntityFromTargetLink(targetLinkUrl, EXERCISE_PATH_PATTERN, exerciseId -> exerciseRepository.findById(Long.valueOf(exerciseId)), "exercise");
    }

    /**
     * Retrieves an Optional of a Lecture referenced by the given target link URL.
     * This method extracts the lecture ID from the URL using the pattern "/courses/{courseId}/lectures/{lectureId}".
     *
     * @param targetLinkUrl The target link URL to retrieve a Lecture.
     * @return An Optional containing the Lecture if found, or an empty Optional otherwise.
     */
    public Optional<Lecture> getLectureFromTargetLink(String targetLinkUrl) {
        LectureRepositoryApi api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));
        return extractEntityFromTargetLink(targetLinkUrl, LECTURE_PATH_PATTERN, lectureId -> api.findById(Long.valueOf(lectureId)), "lecture");
    }

    /**
     * Retrieves an Optional of a Course referenced by the given target link URL.
     * This method extracts the course ID from the URL using the pattern "/courses/{courseId}/**".
     *
     * @param targetLinkUrl The target link URL to retrieve a Course.
     * @return An Optional containing the Course if found, or an empty Optional otherwise.
     */
    public Optional<Course> getCourseFromTargetLink(String targetLinkUrl) {
        return extractEntityFromTargetLink(targetLinkUrl, COURSE_PATH_PATTERN, courseId -> courseRepository.findById(Long.valueOf(courseId)), "course");
    }

    /**
     * Determines the type of content referenced by the target link URL.
     * This method checks the URL path against predefined patterns to identify the type of content.
     *
     * @param targetLinkUrl The target link URL to check.
     * @return The type of content referenced by the URL (e.g., COMPETENCY, LEARNING_PATH, IRIS, or UNKNOWN).
     */
    public DeepLinkingType getTargetLinkType(String targetLinkUrl) {
        AntPathMatcher matcher = new AntPathMatcher();
        String targetLinkPath;

        try {
            targetLinkPath = new URI(targetLinkUrl).getPath();
        }
        catch (URISyntaxException ex) {
            log.info("Malformed target link URL: {}", targetLinkUrl);
            throw new BadRequestAlertException("Malformed target link URL", "LTI", "ltiMalformedUrl");
        }

        for (Map.Entry<String, DeepLinkingType> entry : TARGET_LINK_PATTERNS.entrySet()) {
            if (matcher.match(entry.getKey(), targetLinkPath)) {
                return entry.getValue();
            }
        }

        log.info("No specific content type detected in target link: {}", targetLinkUrl);
        throw new BadRequestAlertException("Content type not found", "LTI", "ltiContentTypeNotFound");
    }

    private void createOrUpdateResourceLaunch(Lti13LaunchRequest launchRequest, User user, Exercise exercise) {
        Optional<LtiResourceLaunch> launchOpt = launchRepository.findByIssAndSubAndDeploymentIdAndResourceLinkId(launchRequest.iss(), launchRequest.sub(),
                launchRequest.deploymentId(), launchRequest.resourceLinkId());

        LtiResourceLaunch launch = launchOpt.orElse(LtiResourceLaunch.from(launchRequest));

        Lti13AgsClaim agsClaim = launchRequest.agsClaim();
        // we do support LTI 1.3 Assigment and Grading Services SCORE publish service
        if (agsClaim != null) {
            launch.setScoreLineItemUrl(agsClaim.lineItem());
        }

        launch.setExercise(exercise);
        launch.setUser(user);

        Optional<LtiPlatformConfiguration> ltiPlatformConfiguration = ltiPlatformConfigurationRepository.findByRegistrationId(launchRequest.clientRegistrationId());
        ltiPlatformConfiguration.ifPresent(launch::setLtiPlatformConfiguration);

        launchRepository.save(launch);
    }

    private void handleLaunchRequest(Lti13LaunchRequest launchRequest, User user, Exercise exercise, OidcIdToken ltiIdToken, OnlineCourseConfiguration onlineCourseConfiguration) {
        createOrUpdateResourceLaunch(launchRequest, user, exercise);

        ltiService.authenticateLtiUser(ltiIdToken.getEmail(), createUsernameFromLaunchRequest(ltiIdToken, onlineCourseConfiguration), ltiIdToken.getGivenName(),
                ltiIdToken.getFamilyName(), onlineCourseConfiguration.isRequireExistingUser());

        if (exercise != null) {
            ltiService.onSuccessfulLtiAuthentication(user, exercise);
        }
    }

    /**
     * Build the response for the LTI launch.
     *
     * @param uriComponentsBuilder the uri builder to add the query params to
     * @param response             the response to add the JWT cookie to
     */
    public void buildLtiResponse(UriComponentsBuilder uriComponentsBuilder, HttpServletResponse response) {
        ltiService.buildLtiResponse(uriComponentsBuilder, response);
    }

    /**
     * Builds a response indicating the need for successful login with the associated username.
     *
     * @param response   The HttpServletResponse object.
     * @param ltiIdToken The OIDC ID token with the LTI email address.
     */
    public void buildLtiEmailInUseResponse(HttpServletResponse response, OidcIdToken ltiIdToken) {
        Optional<String> optionalUsername = artemisAuthenticationProvider.getUsernameForEmail(ltiIdToken.getEmail());

        if (optionalUsername.isPresent()) {
            String sanitizedUsername = getSanitizedUsername(optionalUsername.get());
            response.addHeader("ltiSuccessLoginRequired", sanitizedUsername);
        }
        ltiService.prepareLogoutCookie(response);
    }

    private String getSanitizedUsername(String username) {
        // Remove \r and LF \n characters to prevent HTTP response splitting
        return username.replaceAll("[\r\n]", "");
    }

    public boolean hasTargetLinkWithoutExercise(String targetLinkUrl, Optional<Lecture> targetLecture) {
        DeepLinkingType linkType = getTargetLinkType(targetLinkUrl);
        return linkType == DeepLinkingType.COMPETENCY || linkType == DeepLinkingType.LEARNING_PATH || targetLecture.isPresent() || linkType == DeepLinkingType.IRIS;
    }

    /**
     * Initiates the deep linking process for a course based on the provided LTI ID token and client registration ID.
     *
     * @param ltiIdToken           The ID token containing the deep linking information.
     * @param clientRegistrationId The client registration ID associated with the LTI platform.
     * @throws BadRequestAlertException if LTI is not configured.
     */
    public void startDeepLinking(OidcIdToken ltiIdToken, String clientRegistrationId) {

        Optional<LtiPlatformConfiguration> ltiPlatformConfiguration = ltiPlatformConfigurationRepository.findByRegistrationId(clientRegistrationId);
        if (ltiPlatformConfiguration.isEmpty()) {
            throw new BadRequestAlertException("Configuration not found for this client registration ID:" + clientRegistrationId, "LTI", "ltiNotConfigured");
        }

        ltiService.authenticateLtiUser(ltiIdToken.getEmail(), ltiIdToken.getPreferredUsername(), ltiIdToken.getGivenName(), ltiIdToken.getFamilyName(), true);
    }
}
