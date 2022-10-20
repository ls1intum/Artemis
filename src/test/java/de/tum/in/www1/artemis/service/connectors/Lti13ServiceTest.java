package de.tum.in.www1.artemis.service.connectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lti.Lti13LaunchRequest;
import de.tum.in.www1.artemis.domain.lti.Lti13ResourceLaunch;
import de.tum.in.www1.artemis.domain.lti.Scopes;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.lti.Lti13TokenRetriever;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

public class Lti13ServiceTest {

    private Lti13Service lti13Service;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private Lti13ResourceLaunchRepository launchRepository;

    @Mock
    private LtiService ltiService;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    @Mock
    private Lti13TokenRetriever tokenRetriever;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    public void init() {
        MockitoAnnotations.openMocks(this);
        lti13Service = new Lti13Service(userRepository, exerciseRepository, courseRepository, launchRepository, ltiService, resultRepository, tokenRetriever,
                clientRegistrationRepository, restTemplate);
    }

    @Test
    public void performLaunch_exerciseFound() {
        long exerciseId = 134;
        Exercise exercise = new TextExercise();
        exercise.setId(exerciseId);

        long courseId = 12;
        Course course = new Course();
        course.setId(courseId);
        exercise.setCourse(course);
        doReturn(Optional.of(exercise)).when(exerciseRepository).findById(exerciseId);
        doReturn(Optional.of(course)).when(courseRepository).findById(Long.valueOf(courseId));

        Lti13LaunchRequest launchRequest = Mockito.mock(Lti13LaunchRequest.class);
        doReturn("https://some-artemis-domain.org/courses/" + courseId + "/exercises/" + exerciseId).when(launchRequest).getTargetLinkUri();
        User user = new User();
        doReturn(user).when(userRepository).getUserWithGroupsAndAuthorities();

        // TODO lti13Service.performLaunch(launchRequest);

        assertTrue(user.getGroups().contains(course.getStudentGroupName()), "User was not added to exercise course group");
        verify(userRepository).save(user);
        // TODO verify(authenticationProvider).addUserToGroup(user, course.getStudentGroupName());
    }

    @Test
    public void performLaunch_exerciseNotFound() {
        Lti13LaunchRequest launchRequest = Mockito.mock(Lti13LaunchRequest.class);
        doReturn("https://some-artemis-domain.org/with/invalid/path/to/exercise/11").when(launchRequest).getTargetLinkUri();

        // TODO assertThrows(InternalAuthenticationServiceException.class, () -> lti13Service.performLaunch(launchRequest));
        verify(userRepository, never()).save(any());
        // TODO verify(authenticationProvider, never()).addUserToGroup(any(), any());
    }

    @Test
    public void getExerciseFromTargetLink_valid() {
        long exerciseId = 134;
        Exercise exercise = new TextExercise();
        exercise.setId(exerciseId);

        long courseId = 12;
        Course course = new Course();
        course.setId(courseId);

        exercise.setCourse(course);

        String target = "https://some-artemis-domain.org/courses/" + courseId + "/exercises/" + exerciseId;

        doReturn(Optional.of(exercise)).when(exerciseRepository).findById(Long.valueOf(exerciseId));
        doReturn(Optional.of(course)).when(courseRepository).findById(Long.valueOf(courseId));

        Optional<Exercise> exerciseOpt = lti13Service.getExerciseFromTargetLink(target);
        assertFalse(exerciseOpt.isEmpty(), "TargetLink " + target + " could not be resolved to an exercise");
        assertEquals(exercise, exerciseOpt.get(), "TargetLink " + target + " was not resolved to the intended exercise");
    }

    @Test
    public void getExerciseFromTargetLink_invalidPath() {
        doReturn(Optional.of(new TextExercise())).when(exerciseRepository).findById(any());
        doReturn(Optional.of(new Course())).when(courseRepository).findById(any());

        String[] invalidTargets = new String[] { "https://some-artemis-domain.org/courses/234", "https://some-artemis-domain.org/courses/234/exercises",
                "https://some-artemis-domain.org/exericses/123", "https://some-artemis-domain.org/something/courses/234/exercises/123" };
        Arrays.stream(invalidTargets)
                .forEach(target -> assertTrue(lti13Service.getExerciseFromTargetLink(target).isEmpty(), "Could retrieve an exercise from an invalid target link: " + target));
    }

    @Test
    public void getExerciseFromTargetLink_exerciseCourseMismatch() {
        long exerciseId = 123;
        Exercise exercise = new TextExercise();
        exercise.setId(exerciseId);

        Course exerciseCourse = new Course();
        exercise.setCourse(exerciseCourse);

        long unrelatedCourseId = 12;
        String target = "https://some-artemis-domain.org/courses/" + unrelatedCourseId + "/exercises/" + exerciseId;

        doReturn(Optional.of(exercise)).when(exerciseRepository).findById(exerciseId);
        doReturn(Optional.of(new Course())).when(courseRepository).findById(unrelatedCourseId);

        assertTrue(lti13Service.getExerciseFromTargetLink(target).isEmpty(), "Could retrieve an exercise although the course was wrong: " + target);
    }

    @Test
    public void getExerciseFromTargetLink_courseNotFound() {
        String target = "https://some-artemis-domain.org/courses/12/exercises/123";

        doReturn(Optional.of(new TextExercise())).when(exerciseRepository).findById(any());
        doReturn(Optional.empty()).when(courseRepository).findById(any());

        assertTrue(lti13Service.getExerciseFromTargetLink(target).isEmpty(), "Exercise was returned although course was not found: " + target);
    }

    @Test
    public void onNewResult() throws net.minidev.json.parser.ParseException {
        Result result = new Result();
        double scoreGiven = 60D;// 60%
        result.setScore(scoreGiven);

        State state = getValidStateForNewResult(result);

        Lti13ResourceLaunch launch = state.getLti13ResourceLaunch();
        User user = state.getUser();
        Exercise exercise = state.getExercise();
        StudentParticipation participation = state.getParticipation();
        ClientRegistration clientRegistration = state.getClientRegistration();
        String clientRegistrationId = clientRegistration.getRegistrationId();

        OAuth2AccessTokenResponse accessTokenResponse = getSampleAccessTokenResponse();

        doReturn(Collections.singletonList(launch)).when(launchRepository).findByUserAndExercise(user, exercise);
        doReturn(Optional.of(result)).when(resultRepository).findFirstByParticipationIdOrderByCompletionDateDesc(participation.getId());
        doReturn(clientRegistration).when(clientRegistrationRepository).findByRegistrationId(clientRegistrationId);

        doReturn(accessTokenResponse).when(tokenRetriever).getToken(eq(clientRegistration), eq(launch.getTargetLinkUri()), eq(Scopes.AGS_SCORE));

        lti13Service.onNewResult(participation);

        ArgumentCaptor<URI> uriCapture = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<HttpEntity<String>> httpEntityCapture = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate).postForEntity(uriCapture.capture(), httpEntityCapture.capture(), any());

        HttpEntity<String> httpEntity = httpEntityCapture.getValue();

        List<String> authHeaders = httpEntity.getHeaders().get("Authorization");
        assertNotNull(authHeaders, "Score publish request must contain an Authorization header");
        assertTrue(authHeaders.contains("Bearer " + accessTokenResponse.getAccessToken().getTokenValue()),
                "Score publish request must contain the corresponding Authorization Bearer token");

        JSONParser jsonParser = new JSONParser(JSONParser.MODE_JSON_SIMPLE);
        JSONObject body = (JSONObject) jsonParser.parse(httpEntity.getBody());
        assertEquals(launch.getSub(), body.get("userId"), "Invalid parameter in score publish request: userId");
        assertNotNull(body.get("timestamp"), "Parameter missing in score publish request: timestamp");
        assertNotNull(body.get("activityProgress"), "Parameter missing in score publish request: activityProgress");
        assertNotNull(body.get("gradingProgress"), "Parameter missing in score publish request: gradingProgress");

        // any score that is submitted is relative to a maxScore of 1. With that the LTI Platform can simply scale the
        // score with its own max score value.
        assertEquals(scoreGiven / 100D, body.get("scoreGiven"), "Invalid parameter in score publish request: scoreGiven");
        assertEquals((1D), body.get("scoreMaximum"), "Invalid parameter in score publish request: scoreMaximum");

        URI scoreUri = uriCapture.getValue();
        assertEquals(launch.getScoreLineItemUrl(), scoreUri.toString(), "Score publish request was sent to a wrong URI");
    }

    private State getValidStateForNewResult(Result result) {
        User user = new User();
        user.setLogin("someone");

        Exercise exercise = new ProgrammingExercise() {
        };

        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(user);
        participation.setId(123L);

        String targetLinkUri = "https://some-artemis-domain.org/courses/12/exercises/123";

        String clientRegistrationId = "some-client-registration";
        ClientRegistration clientRegistration = Mockito.mock(ClientRegistration.class);
        doReturn(clientRegistrationId).when(clientRegistration).getRegistrationId();

        Lti13ResourceLaunch launch = new Lti13ResourceLaunch();
        launch.setTargetLinkUri(targetLinkUri);
        launch.setUser(user);
        launch.setSub("some-sub");
        launch.setClientRegistrationId(clientRegistration.getRegistrationId());
        launch.setExercise(exercise);
        launch.setScoreLineItemUrl("https://some-lti-platform/some/lineitem/scores");

        return new State(launch, exercise, user, participation, result, clientRegistration);
    }

    private OAuth2AccessTokenResponse getSampleAccessTokenResponse() {
        OAuth2AccessTokenResponse accessTokenResponse = Mockito.mock(OAuth2AccessTokenResponse.class);
        OAuth2AccessToken accessToken = Mockito.mock(OAuth2AccessToken.class);
        doReturn(accessToken).when(accessTokenResponse).getAccessToken();
        String accessTokenValue = "some-bearer-token";
        doReturn(accessTokenValue).when(accessToken).getTokenValue();

        return accessTokenResponse;
    }

    /**
     * A wrapper for Entities that are related to each other.
     */
    private static class State {

        private Lti13ResourceLaunch lti13ResourceLaunch;

        private Exercise exercise;

        private User user;

        private StudentParticipation participation;

        private Result result;

        private ClientRegistration clientRegistration;

        public State(Lti13ResourceLaunch lti13ResourceLaunch, Exercise exercise, User user, StudentParticipation participation, Result result,
                ClientRegistration clientRegistration) {
            this.lti13ResourceLaunch = lti13ResourceLaunch;
            this.exercise = exercise;
            this.user = user;
            this.participation = participation;
            this.result = result;
            this.clientRegistration = clientRegistration;
        }

        public Lti13ResourceLaunch getLti13ResourceLaunch() {
            return lti13ResourceLaunch;
        }

        public Exercise getExercise() {
            return exercise;
        }

        public User getUser() {
            return user;
        }

        public StudentParticipation getParticipation() {
            return participation;
        }

        public Result getResult() {
            return result;
        }

        public ClientRegistration getClientRegistration() {
            return clientRegistration;
        }
    }
}
