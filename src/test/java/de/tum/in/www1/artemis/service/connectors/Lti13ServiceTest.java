package de.tum.in.www1.artemis.service.connectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.client.RestTemplate;

import com.nimbusds.jose.shaded.json.JSONObject;
import com.nimbusds.jose.shaded.json.parser.JSONParser;
import com.nimbusds.jose.shaded.json.parser.ParseException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lti.LtiResourceLaunch;
import de.tum.in.www1.artemis.domain.lti.Scopes;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.OAuth2JWKSService;
import de.tum.in.www1.artemis.security.lti.Lti13TokenRetriever;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import uk.ac.ox.ctl.lti13.lti.Claims;

class Lti13ServiceTest {

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
    private OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    @Mock
    private LtiService ltiService;

    @Mock
    private OAuth2JWKSService oAuth2JWKSService;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    @Mock
    private Lti13TokenRetriever tokenRetriever;

    @Mock
    private RestTemplate restTemplate;

    private String clientRegistrationId;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        lti13Service = new Lti13Service(userRepository, exerciseRepository, courseRepository, launchRepository, onlineCourseConfigurationRepository, ltiService, oAuth2JWKSService,
                resultRepository, tokenRetriever, clientRegistrationRepository, restTemplate);
        clientRegistrationId = "clientId";
    }

    @Test
    void performLaunch_exerciseFound() {
        long exerciseId = 134;
        Exercise exercise = new TextExercise();
        exercise.setId(exerciseId);

        long courseId = 12;
        Course course = new Course();
        course.setId(courseId);
        course.setOnlineCourseConfiguration(new OnlineCourseConfiguration());
        exercise.setCourse(course);
        doReturn(Optional.of(exercise)).when(exerciseRepository).findById(exerciseId);
        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);

        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        when(oidcIdToken.getEmail()).thenReturn("testuser@email.com");
        when(oidcIdToken.getClaim("sub")).thenReturn("1");
        when(oidcIdToken.getClaim("iss")).thenReturn("http://otherDomain.com");
        when(oidcIdToken.getClaim(Claims.LTI_DEPLOYMENT_ID)).thenReturn("1");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "resourceLinkUrl");
        when(oidcIdToken.getClaim(Claims.RESOURCE_LINK)).thenReturn(jsonObject);
        when(oidcIdToken.getClaim(Claims.TARGET_LINK_URI)).thenReturn("https://some-artemis-domain.org/courses/" + courseId + "/exercises/" + exerciseId);

        User user = new User();
        doReturn(user).when(userRepository).getUserWithGroupsAndAuthorities();
        doNothing().when(ltiService).authenticateLtiUser(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
        doNothing().when(ltiService).onSuccessfulLtiAuthentication(any(), any(), any());

        lti13Service.performLaunch(oidcIdToken, clientRegistrationId);

        verify(launchRepository).findByIssAndSubAndDeploymentIdAndResourceLinkId("http://otherDomain.com", "1", "1", "resourceLinkUrl");
        verify(launchRepository).save(any());
    }

    @Test
    void performLaunch_exerciseNotFound() {
        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        doReturn("https://some-artemis-domain.org/with/invalid/path/to/exercise/11").when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

        assertThrows(BadRequestAlertException.class, () -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://some-artemis-domain.org/courses/234", "https://some-artemis-domain.org/courses/234/exercises",
            "https://some-artemis-domain.org/exericses/123", "https://some-artemis-domain.org/something/courses/234/exercises/123" })
    void performLaunch_invalidPath(String invalidPath) {
        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        doReturn(invalidPath).when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

        assertThrows(BadRequestAlertException.class, () -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));
    }

    @Test
    void performLaunch_courseNotFound() {
        long exerciseId = 134;
        Exercise exercise = new TextExercise();
        exercise.setId(exerciseId);
        Course course = new Course();
        course.setId(1000L);
        exercise.setCourse(course);

        doReturn(Optional.of(exercise)).when(exerciseRepository).findById(any());
        doThrow(EntityNotFoundException.class).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(1000L);

        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        String target = "https://some-artemis-domain.org/courses/12/exercises/123";
        doReturn(target).when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

        assertThrows(EntityNotFoundException.class, () -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));
    }

    @Test
    void performLaunch_notOnlineCourse() {
        long exerciseId = 134;
        Exercise exercise = new TextExercise();
        exercise.setId(exerciseId);

        long courseId = 12;
        Course course = new Course();
        course.setId(courseId);
        exercise.setCourse(course);
        doReturn(Optional.of(exercise)).when(exerciseRepository).findById(exerciseId);
        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);

        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        doReturn("https://some-artemis-domain.org/courses/" + courseId + "/exercises/" + exerciseId).when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

        assertThrows(BadRequestAlertException.class, () -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));
    }

    @Test
    void onNewResult() throws ParseException {
        Result result = new Result();
        double scoreGiven = 60D;
        result.setScore(scoreGiven);

        Feedback feedback1 = new Feedback();
        Feedback feedback2 = new Feedback();
        feedback1.setDetailText("Good job");
        feedback2.setDetailText("Not so good");

        result.addFeedback(feedback1);
        result.addFeedback(feedback2);

        State state = getValidStateForNewResult(result);

        LtiResourceLaunch launch = state.getLti13ResourceLaunch();
        User user = state.getUser();
        Exercise exercise = state.getExercise();
        StudentParticipation participation = state.getParticipation();
        ClientRegistration clientRegistration = state.getClientRegistration();
        String clientRegistrationId = clientRegistration.getRegistrationId();

        doReturn(Collections.singletonList(launch)).when(launchRepository).findByUserAndExercise(user, exercise);
        doReturn(Optional.of(result)).when(resultRepository).findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(participation.getId());
        doReturn(clientRegistration).when(clientRegistrationRepository).findByRegistrationId(clientRegistrationId);

        String accessToken = "accessToken";
        doReturn(accessToken).when(tokenRetriever).getToken(eq(clientRegistration), eq(Scopes.AGS_SCORE));

        lti13Service.onNewResult(participation);

        ArgumentCaptor<String> urlCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity<String>> httpEntityCapture = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate).postForEntity(urlCapture.capture(), httpEntityCapture.capture(), any());

        HttpEntity<String> httpEntity = httpEntityCapture.getValue();

        List<String> authHeaders = httpEntity.getHeaders().get("Authorization");
        assertNotNull(authHeaders, "Score publish request must contain an Authorization header");
        assertTrue(authHeaders.contains("Bearer " + accessToken), "Score publish request must contain the corresponding Authorization Bearer token");

        JSONParser jsonParser = new JSONParser(JSONParser.MODE_JSON_SIMPLE);
        JSONObject body = (JSONObject) jsonParser.parse(httpEntity.getBody());
        assertEquals(launch.getSub(), body.get("userId"), "Invalid parameter in score publish request: userId");
        assertNotNull(body.get("timestamp"), "Parameter missing in score publish request: timestamp");
        assertNotNull(body.get("activityProgress"), "Parameter missing in score publish request: activityProgress");
        assertNotNull(body.get("gradingProgress"), "Parameter missing in score publish request: gradingProgress");

        assertEquals("Good job. Not so good", body.get("comment"), "Invalid parameter in score publish request: comment");
        assertEquals(scoreGiven, body.get("scoreGiven"), "Invalid parameter in score publish request: scoreGiven");
        assertEquals(100d, body.get("scoreMaximum"), "Invalid parameter in score publish request: scoreMaximum");

        assertEquals(urlCapture.getValue(), launch.getScoreLineItemUrl() + "/scores", "Score publish request was sent to a wrong URI");
    }

    private State getValidStateForNewResult(Result result) {
        User user = new User();
        user.setLogin("someone");

        Exercise exercise = new ProgrammingExercise() {
        };
        exercise.setMaxPoints(80d);

        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(user);
        participation.setId(123L);

        String targetLinkUri = "https://some-artemis-domain.org/courses/12/exercises/123";

        String clientRegistrationId = "some-client-registration";
        ClientRegistration clientRegistration = mock(ClientRegistration.class);
        doReturn(clientRegistrationId).when(clientRegistration).getRegistrationId();

        LtiResourceLaunch launch = new LtiResourceLaunch();
        launch.setTargetLinkUri(targetLinkUri);
        launch.setUser(user);
        launch.setSub("some-sub");
        launch.setClientRegistrationId(clientRegistration.getRegistrationId());
        launch.setExercise(exercise);
        launch.setScoreLineItemUrl("https://some-lti-platform/some/lineitem");

        return new State(launch, exercise, user, participation, result, clientRegistration);
    }

    /**
     * A wrapper for Entities that are related to each other.
     */
    private static class State {

        private LtiResourceLaunch ltiResourceLaunch;

        private Exercise exercise;

        private User user;

        private StudentParticipation participation;

        private Result result;

        private ClientRegistration clientRegistration;

        public State(LtiResourceLaunch ltiResourceLaunch, Exercise exercise, User user, StudentParticipation participation, Result result, ClientRegistration clientRegistration) {
            this.ltiResourceLaunch = ltiResourceLaunch;
            this.exercise = exercise;
            this.user = user;
            this.participation = participation;
            this.result = result;
            this.clientRegistration = clientRegistration;
        }

        public LtiResourceLaunch getLti13ResourceLaunch() {
            return ltiResourceLaunch;
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
