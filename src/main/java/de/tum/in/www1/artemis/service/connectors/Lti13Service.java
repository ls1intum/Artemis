package de.tum.in.www1.artemis.service.connectors;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lti.Lti13AgsClaim;
import de.tum.in.www1.artemis.domain.lti.Lti13LaunchRequest;
import de.tum.in.www1.artemis.domain.lti.Lti13ResourceLaunch;
import de.tum.in.www1.artemis.domain.lti.Scopes;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.lti.Lti13TokenRetriever;
import net.minidev.json.JSONObject;

@Service
public class Lti13Service {

    private final String EXERCISE_PATH_PATTERN = "/courses/{courseId}/exercises/{exerciseId}";

    private final Logger log = LoggerFactory.getLogger(Lti13Service.class);

    private UserRepository userRepository;

    private ExerciseRepository exerciseRepository;

    private CourseRepository courseRepository;

    private Lti13ResourceLaunchRepository launchRepository;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private ResultRepository resultRepository;

    private Lti13TokenRetriever tokenRetriever;

    private ClientRegistrationRepository clientRegistrationRepository;

    private RestTemplate rest;

    public Lti13Service(UserRepository userRepository, ExerciseRepository exerciseRepository, CourseRepository courseRepository,
            ArtemisAuthenticationProvider authenticationProvider, Lti13ResourceLaunchRepository launchRepository, ResultRepository resultRepository,
            Lti13TokenRetriever tokenRetriever, ClientRegistrationRepository clientRegistrationRepository, RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
        this.artemisAuthenticationProvider = authenticationProvider;
        this.launchRepository = launchRepository;
        this.resultRepository = resultRepository;
        this.tokenRetriever = tokenRetriever;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.rest = restTemplate;
    }

    /**
     * Performs an LTI 1.3 exercise launch with the LTI parameters contained in launchRequest
     * If the launch was successful the user is added to the target exercise group (e.g. the course).
     *
     * @param launchRequest
     */
    public void performLaunch(Lti13LaunchRequest launchRequest) {
        Optional<Exercise> targetExercise = getExerciseFromTargetLink(launchRequest.getTargetLinkUri());
        if (targetExercise.isEmpty()) {
            String message = "No exercise to launch at " + launchRequest.getTargetLinkUri();
            log.error(message);
            throw new InternalAuthenticationServiceException(message);
        }

        Course course = targetExercise.get().getCourseViaExerciseGroupOrCourseMember();
        User user = userRepository.getUserWithGroupsAndAuthorities();

        addUserToExerciseGroup(user, course);

        createOrUpdateResourceLaunch(launchRequest, user, targetExercise.get());
    }

    /**
     * This method is pinged on new exercise results. It sends a message to the related LTI 1.3 platforms with the new score.
     *
     * @param participation The exercise participation for which a new result is available
     */
    public void onNewResult(StudentParticipation participation) {
        var students = participation.getStudents();

        if (students != null) {
            students.forEach(student -> {
                // there can be multiple launches for one exercise and student if the student has used more than one LTI 1.3 platform
                // to launch the exercise (for example multiple lms)
                Collection<Lti13ResourceLaunch> launches = launchRepository.findByUserAndExercise(student, participation.getExercise());

                if (launches.isEmpty()) {
                    return;
                }

                Optional<Result> result = resultRepository.findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(participation.getId());

                if (result.isEmpty()) {
                    log.error("onNewResult triggered for participation " + participation.getId() + " but no result could be found");
                    return;
                }

                String concatenatedFeedbacks = result.get().getFeedbacks().stream().map(Feedback::getDetailText).collect(Collectors.joining(" "));

                launches.forEach(launch -> submitScore(launch, concatenatedFeedbacks, result.get().getScore()));
            });
        }
    }

    protected void submitScore(Lti13ResourceLaunch launch, String comment, Double score) {
        String scoreLineItemUrl = launch.getScoreLineItemUrl();
        if (scoreLineItemUrl == null) {
            return;
        }

        ClientRegistration client = clientRegistrationRepository.findByRegistrationId(launch.getClientRegistrationId());
        if (client == null) {
            log.error("Could not transmit score to " + launch.getClientRegistrationId() + ": client registration not found");
            return;
        }

        String token = tokenRetriever.getToken(client, Scopes.AGS_SCORE);

        if (token == null) {
            log.error("Could not transmit score to " + client.getClientId() + ": missing token");
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/vnd.ims.lis.v1.score+json"));
        headers.setBearerAuth(token);
        String body = getScoreBody(launch.getSub(), comment, score);
        HttpEntity<String> httpRequest = new HttpEntity<>(body, headers);
        try {
            rest.postForEntity(new URI(getScoresUrl(launch.getScoreLineItemUrl())), httpRequest, Object.class);
            log.info("Submitted score for " + launch.getUser().getLogin() + " to client" + client.getClientId());
        }
        catch (Exception e) {
            String message = "Could not submit score for " + launch.getUser().getLogin() + " to client " + client.getClientId() + ": " + e.getMessage();
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
    protected Optional<Exercise> getExerciseFromTargetLink(String targetLinkUrl) {
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

        String courseId = pathVariables.get("courseId");
        String exerciseId = pathVariables.get("exerciseId");

        Optional<Exercise> exerciseOpt = exerciseRepository.findById(Long.valueOf(exerciseId));
        Optional<Course> courseOpt = courseRepository.findById(Long.valueOf(courseId));

        if (exerciseOpt.isEmpty() || courseOpt.isEmpty()) {
            log.info("Could not find exercise or course for target link url: " + targetLinkUrl);
            return Optional.empty();
        }

        Exercise exercise = exerciseOpt.get();
        Course course = courseOpt.get();

        if (!course.equals(exercise.getCourseViaExerciseGroupOrCourseMember())) {
            log.info("Exercise is not related to course for target link url: " + targetLinkUrl);
            return Optional.empty();
        }

        return exerciseOpt;
    }

    private void addUserToExerciseGroup(User user, Course course) {
        String courseStudentGroupName = course.getStudentGroupName();
        if (!user.getGroups().contains(courseStudentGroupName)) {
            Set<String> groups = user.getGroups();
            groups.add(courseStudentGroupName);
            user.setGroups(groups);
            userRepository.save(user);

            try {
                artemisAuthenticationProvider.addUserToGroup(user, courseStudentGroupName);
            }
            catch (ArtemisAuthenticationException e) {
                // This might throw exceptions, for example if the group does not exist on the authentication service. We can safely ignore it
            }
        }
    }

    private void createOrUpdateResourceLaunch(Lti13LaunchRequest launchRequest, User user, Exercise exercise) {
        Optional<Lti13ResourceLaunch> launchOpt = launchRepository.findByIssAndSubAndDeploymentIdAndResourceLinkId(launchRequest.getIss(), launchRequest.getSub(),
                launchRequest.getDeploymentId(), launchRequest.getResourceLinkId());

        Lti13ResourceLaunch launch = launchOpt.orElse(Lti13ResourceLaunch.from(launchRequest));

        Lti13AgsClaim agsClaim = launchRequest.getAgsClaim();
        // we do support LTI 1.3 Assigment and Grading Services SCORE publish service
        if (agsClaim != null && agsClaim.getScope().contains(Lti13AgsClaim.Scope.SCORE)) {
            launch.setScoreLineItemUrl(agsClaim.getLineItem());
        }

        launch.setExercise(exercise);
        launch.setUser(user);
        launch.setTargetLinkUri(launchRequest.getTargetLinkUri());
        launchRepository.save(launch);
    }
}
