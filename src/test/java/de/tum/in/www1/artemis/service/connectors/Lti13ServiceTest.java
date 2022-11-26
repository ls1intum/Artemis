package de.tum.in.www1.artemis.service.connectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.nimbusds.jose.shaded.json.JSONObject;
import com.nimbusds.jose.shaded.json.parser.JSONParser;
import com.nimbusds.jose.shaded.json.parser.ParseException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lti.LtiResourceLaunch;
import de.tum.in.www1.artemis.domain.lti.Scopes;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.lti.Lti13TokenRetriever;
import de.tum.in.www1.artemis.service.OnlineCourseConfigurationService;
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
    private LtiService ltiService;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private OnlineCourseConfigurationService onlineCourseConfigurationService;

    @Mock
    private Lti13TokenRetriever tokenRetriever;

    @Mock
    private RestTemplate restTemplate;

    private OidcIdToken oidcIdToken;

    private String clientRegistrationId;

    private OnlineCourseConfiguration onlineCourseConfiguration;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        lti13Service = new Lti13Service(userRepository, exerciseRepository, courseRepository, launchRepository, ltiService, resultRepository, tokenRetriever,
                onlineCourseConfigurationService, restTemplate);
        clientRegistrationId = "clientId";
        onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setUserPrefix("prefix");
        oidcIdToken = mock(OidcIdToken.class);
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
        doNothing().when(ltiService).authenticateLtiUser(any(), any(), any(), any(), any(), anyBoolean());
        doNothing().when(ltiService).onSuccessfulLtiAuthentication(any(), any(), any());

        lti13Service.performLaunch(oidcIdToken, clientRegistrationId);

        verify(launchRepository).findByIssAndSubAndDeploymentIdAndResourceLinkId("http://otherDomain.com", "1", "1", "resourceLinkUrl");
        verify(launchRepository).save(any());
    }

    @Test
    void performLaunch_invalidToken() {
        long exerciseId = 134;
        Exercise exercise = new TextExercise();
        exercise.setId(exerciseId);

        long courseId = 12;
        Course course = new Course();
        course.setId(courseId);
        course.setOnlineCourseConfiguration(onlineCourseConfiguration);
        exercise.setCourse(course);
        doReturn(Optional.of(exercise)).when(exerciseRepository).findById(exerciseId);
        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);

        when(oidcIdToken.getEmail()).thenReturn("testuser@email.com");
        when(oidcIdToken.getClaim(Claims.TARGET_LINK_URI)).thenReturn("https://some-artemis-domain.org/courses/" + courseId + "/exercises/" + exerciseId);

        User user = new User();
        doReturn(user).when(userRepository).getUserWithGroupsAndAuthorities();
        doNothing().when(ltiService).authenticateLtiUser(any(), any(), any(), any(), any(), anyBoolean());
        doNothing().when(ltiService).onSuccessfulLtiAuthentication(any(), any(), any());

        assertThrows(IllegalArgumentException.class, () -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));

        verifyNoInteractions(launchRepository);
    }

    @Test
    void performLaunch_exerciseNotFound() {
        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        doReturn("https://some-artemis-domain.org/courses/1/exercises/100000").when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

        assertThrows(BadRequestAlertException.class, () -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));
        verify(userRepository, never()).save(any());
    }

    @Test
    void performLaunch_exerciseNotFoundInvalidPath() {
        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        doReturn("https://some-artemis-domain.org/with/invalid/path/to/exercise/11").when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

        assertThrows(BadRequestAlertException.class, () -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));
        verify(userRepository, never()).save(any());
    }

    @Test
    void performLaunch_malformedUrl() {
        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        doReturn("path").when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

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
    void createUsernameFromLaunchRequest_fromUsername() {
        when(oidcIdToken.getPreferredUsername()).thenReturn("john");

        String username = lti13Service.createUsernameFromLaunchRequest(oidcIdToken, onlineCourseConfiguration);

        assertEquals("prefix_john", username);
    }

    @Test
    void createUsernameFromLaunchRequest_fromFullname() {
        when(oidcIdToken.getPreferredUsername()).thenReturn("");
        when(oidcIdToken.getGivenName()).thenReturn("jon");
        when(oidcIdToken.getFamilyName()).thenReturn("snow");

        String username = lti13Service.createUsernameFromLaunchRequest(oidcIdToken, onlineCourseConfiguration);

        assertEquals("prefix_jonsnow", username);
    }

    @Test
    void createUsernameFromLaunchRequest_fromEmailNoFamilyName() {
        when(oidcIdToken.getPreferredUsername()).thenReturn("");
        when(oidcIdToken.getGivenName()).thenReturn("john");
        when(oidcIdToken.getFamilyName()).thenReturn("");
        when(oidcIdToken.getEmail()).thenReturn("jon.snow@email.com");

        String username = lti13Service.createUsernameFromLaunchRequest(oidcIdToken, onlineCourseConfiguration);

        assertEquals("prefix_jon.snow", username);
    }

    @Test
    void createUsernameFromLaunchRequest_fromEmail() {
        when(oidcIdToken.getPreferredUsername()).thenReturn("");
        when(oidcIdToken.getGivenName()).thenReturn("");
        when(oidcIdToken.getFamilyName()).thenReturn("");
        when(oidcIdToken.getEmail()).thenReturn("jon.snow@email.com");

        String username = lti13Service.createUsernameFromLaunchRequest(oidcIdToken, onlineCourseConfiguration);

        assertEquals("prefix_jon.snow", username);
    }

    @Test
    void buildLtiResponseCallLtiService() {

        lti13Service.buildLtiResponse(UriComponentsBuilder.newInstance(), mock(HttpServletResponse.class));

        verify(ltiService, times(1)).buildLtiResponse(any(), any());
    }

    @Test
    void onNewResultNoOnlineCourseConfiguration() {
        Course course = new Course();
        course.setId(1L);
        Exercise exercise = new ProgrammingExercise() {
        };
        exercise.setCourse(course);
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);

        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        doReturn(null).when(onlineCourseConfigurationService).getClientRegistration(any());

        lti13Service.onNewResult(participation);

        verifyNoInteractions(resultRepository);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void onNewResultNoLaunchesForUser() {
        Course course = new Course();
        course.setId(1L);
        User user = new User();
        user.setId(1L);
        Exercise exercise = new ProgrammingExercise() {
        };
        exercise.setCourse(course);
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(user);
        participation.setId(1L);

        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        doReturn(mock(ClientRegistration.class)).when(onlineCourseConfigurationService).getClientRegistration(any());
        doReturn(Collections.emptyList()).when(launchRepository).findByUserAndExercise(user, exercise);

        lti13Service.onNewResult(participation);

        verifyNoInteractions(resultRepository);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void onNewResultNoResultForUser() {
        Course course = new Course();
        course.setId(1L);
        User user = new User();
        user.setId(1L);
        Exercise exercise = new ProgrammingExercise() {
        };
        exercise.setCourse(course);
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(user);
        participation.setId(1L);
        LtiResourceLaunch launch = new LtiResourceLaunch();
        ClientRegistration clientRegistration = mock(ClientRegistration.class);

        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        doReturn(clientRegistration).when(onlineCourseConfigurationService).getClientRegistration(any());
        doReturn(Collections.singletonList(launch)).when(launchRepository).findByUserAndExercise(user, exercise);
        doReturn(Optional.empty()).when(resultRepository).findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(participation.getId());

        lti13Service.onNewResult(participation);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void onNewResultNoScoreUrl() {
        Result result = new Result();
        double scoreGiven = 60D;
        result.setScore(scoreGiven);

        Feedback feedback = new Feedback();
        feedback.setDetailText("Not so good");
        result.addFeedback(feedback);

        Course course = new Course();
        course.setId(1L);
        User user = new User();
        user.setId(1L);
        Exercise exercise = new ProgrammingExercise() {
        };
        exercise.setCourse(course);
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(user);
        participation.setId(1L);
        LtiResourceLaunch launch = new LtiResourceLaunch();
        ClientRegistration clientRegistration = mock(ClientRegistration.class);

        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        doReturn(clientRegistration).when(onlineCourseConfigurationService).getClientRegistration(any());
        doReturn(Collections.singletonList(launch)).when(launchRepository).findByUserAndExercise(user, exercise);
        doReturn(Optional.of(result)).when(resultRepository).findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(participation.getId());

        lti13Service.onNewResult(participation);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void onNewResultTokenFails() {
        Result result = new Result();
        double scoreGiven = 60D;
        result.setScore(scoreGiven);

        Feedback feedback = new Feedback();
        feedback.setDetailText("Not so good");
        result.addFeedback(feedback);

        State state = getValidStateForNewResult(result);
        LtiResourceLaunch launch = state.getLti13ResourceLaunch();
        User user = state.getUser();
        Exercise exercise = state.getExercise();
        StudentParticipation participation = state.getParticipation();
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        ClientRegistration clientRegistration = state.getClientRegistration();

        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        doReturn(clientRegistration).when(onlineCourseConfigurationService).getClientRegistration(any());
        doReturn(Collections.singletonList(launch)).when(launchRepository).findByUserAndExercise(user, exercise);
        doReturn(Optional.of(result)).when(resultRepository).findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(participation.getId());
        doReturn(null).when(tokenRetriever).getToken(eq(clientRegistration), eq(Scopes.AGS_SCORE));

        lti13Service.onNewResult(participation);

        verifyNoInteractions(restTemplate);
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
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        ClientRegistration clientRegistration = state.getClientRegistration();

        doReturn(Collections.singletonList(launch)).when(launchRepository).findByUserAndExercise(user, exercise);
        doReturn(Optional.of(result)).when(resultRepository).findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(participation.getId());
        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());

        doReturn(clientRegistration).when(onlineCourseConfigurationService).getClientRegistration(any());

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

        Course course = new Course();
        course.setId(1L);

        Exercise exercise = new ProgrammingExercise() {
        };
        exercise.setMaxPoints(80d);
        exercise.setCourse(course);

        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(user);
        participation.setId(123L);

        String clientRegistrationId = "some-client-registration";
        ClientRegistration clientRegistration = mock(ClientRegistration.class);
        doReturn(clientRegistrationId).when(clientRegistration).getRegistrationId();

        LtiResourceLaunch launch = new LtiResourceLaunch();
        launch.setUser(user);
        launch.setSub("some-sub");
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
