package de.tum.cit.aet.artemis.lti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.ArtemisAuthenticationProvider;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lti.config.Lti13TokenRetriever;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.domain.LtiResourceLaunch;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.lti.dto.Scopes;
import de.tum.cit.aet.artemis.lti.repository.Lti13ResourceLaunchRepository;
import de.tum.cit.aet.artemis.lti.service.DeepLinkingType;
import de.tum.cit.aet.artemis.lti.service.Lti13Service;
import de.tum.cit.aet.artemis.lti.service.LtiService;
import de.tum.cit.aet.artemis.lti.service.OnlineCourseConfigurationService;
import de.tum.cit.aet.artemis.lti.test_repository.LtiPlatformConfigurationTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import uk.ac.ox.ctl.lti13.lti.Claims;

class Lti13ServiceTest {

    private Lti13Service lti13Service;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private ExerciseTestRepository exerciseRepository;

    @Mock
    private LectureRepository lectureRepository;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private Lti13ResourceLaunchRepository launchRepository;

    @Mock
    private LtiService ltiService;

    @Mock
    private ResultTestRepository resultRepository;

    @Mock
    private OnlineCourseConfigurationService onlineCourseConfigurationService;

    @Mock
    private Lti13TokenRetriever tokenRetriever;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ArtemisAuthenticationProvider artemisAuthenticationProvider;

    @Mock
    private LtiPlatformConfigurationTestRepository ltiPlatformConfigurationRepository;

    private OidcIdToken oidcIdToken;

    private String clientRegistrationId;

    private OnlineCourseConfiguration onlineCourseConfiguration;

    private AutoCloseable closeable;

    private LtiPlatformConfiguration ltiPlatformConfiguration;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        lti13Service = new Lti13Service(userRepository, exerciseRepository, lectureRepository, courseRepository, launchRepository, ltiService, resultRepository, tokenRetriever,
                onlineCourseConfigurationService, restTemplate, artemisAuthenticationProvider, ltiPlatformConfigurationRepository);
        clientRegistrationId = "clientId";
        onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setUserPrefix("prefix");
        oidcIdToken = mock(OidcIdToken.class);

        ltiPlatformConfiguration = new LtiPlatformConfiguration();
        ltiPlatformConfiguration.setRegistrationId("client-registration");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(userRepository, exerciseRepository, courseRepository, launchRepository, ltiService, resultRepository, onlineCourseConfigurationService, tokenRetriever, restTemplate);
    }

    @Test
    void performLaunch_exerciseFound() {
        MockExercise result = getMockExercise(true);

        when(oidcIdToken.getClaim("sub")).thenReturn("1");
        when(oidcIdToken.getClaim("iss")).thenReturn("https://otherDomain.com");
        when(oidcIdToken.getClaim(Claims.LTI_DEPLOYMENT_ID)).thenReturn("1");
        ObjectNode jsonObject = new ObjectMapper().createObjectNode();
        jsonObject.put("id", "resourceLinkUrl");
        when(oidcIdToken.getClaim(Claims.RESOURCE_LINK)).thenReturn(jsonObject);
        prepareForPerformExerciseLaunch(result.courseId(), result.exerciseId(), true);

        lti13Service.performLaunch(oidcIdToken, clientRegistrationId);

        verify(launchRepository).findByIssAndSubAndDeploymentIdAndResourceLinkId("https://otherDomain.com", "1", "1", "resourceLinkUrl");
        verify(launchRepository).save(any());
    }

    @Test
    void performLaunch_invalidToken() {
        MockExercise exercise = this.getMockExercise(true);
        prepareForPerformExerciseLaunch(exercise.courseId, exercise.exerciseId, true);

        assertThatIllegalArgumentException().isThrownBy(() -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));

        verifyNoInteractions(launchRepository);
    }

    @Test
    void performLaunch_exerciseNotFound() {
        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        doReturn("https://some-artemis-domain.org/courses/1/exercises/100000").when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));

        verify(userRepository, never()).save(any());
    }

    @Test
    void performLaunch_exerciseNotFoundInvalidPath() {
        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        doReturn("https://some-artemis-domain.org/with/invalid/path/to/exercise/11").when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));

        verify(userRepository, never()).save(any());
    }

    @Test
    void performLaunch_malformedUrl() {
        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        doReturn("path").when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));

        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://some-artemis-domain.org/courses/234", "https://some-artemis-domain.org/courses/234/exercises",
            "https://some-artemis-domain.org/exericses/123", "https://some-artemis-domain.org/something/courses/234/exercises/123" })
    void performLaunch_invalidPath(String invalidPath) {
        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        doReturn(invalidPath).when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));
    }

    @Test
    void performLaunch_courseNotFound() {
        long courseId = 1000L;
        long exerciseId = 123L;
        prepareForPerformExerciseLaunch(courseId, exerciseId, true);

        when(courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId))
                .thenThrow(new BadRequestAlertException("Course not found", "LTI", "ltiCourseNotFound"));
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId)).withMessage("Course not found")
                .satisfies(exception -> {
                    assertThat(exception.getEntityName()).isEqualTo("LTI");
                    assertThat(exception.getErrorKey()).isEqualTo("ltiCourseNotFound");
                });
    }

    @Test
    void performLaunch_notOnlineCourse() {
        MockExercise exercise = this.getMockExercise(false);
        OidcIdToken oidcIdToken = mock(OidcIdToken.class);
        doReturn("https://some-artemis-domain.org/courses/" + exercise.courseId + "/exercises/" + exercise.exerciseId).when(oidcIdToken).getClaim(Claims.TARGET_LINK_URI);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId));
    }

    @Test
    void performLaunch_hasTargetLinkWithoutExerciseCalled() {
        when(oidcIdToken.getClaim("sub")).thenReturn("1");
        when(oidcIdToken.getClaim("iss")).thenReturn("https://otherDomain.com");
        when(oidcIdToken.getClaim(Claims.LTI_DEPLOYMENT_ID)).thenReturn("1");
        ObjectNode jsonObject = new ObjectMapper().createObjectNode();
        jsonObject.put("id", "resourceLinkUrl");
        when(oidcIdToken.getClaim(Claims.RESOURCE_LINK)).thenReturn(jsonObject);
        prepareForPerformCompetencyLaunch(1L, true);

        lti13Service.performLaunch(oidcIdToken, clientRegistrationId);

        verify(launchRepository).findByIssAndSubAndDeploymentIdAndResourceLinkId("https://otherDomain.com", "1", "1", "resourceLinkUrl");
        verify(launchRepository).save(any());
    }

    @Test
    void performLaunch_onlineCourseConfigurationNull() {
        when(oidcIdToken.getClaim("sub")).thenReturn("1");
        when(oidcIdToken.getClaim("iss")).thenReturn("https://otherDomain.com");
        when(oidcIdToken.getClaim(Claims.LTI_DEPLOYMENT_ID)).thenReturn("1");
        ObjectNode jsonObject = new ObjectMapper().createObjectNode();
        jsonObject.put("id", "resourceLinkUrl");
        when(oidcIdToken.getClaim(Claims.RESOURCE_LINK)).thenReturn(jsonObject);
        prepareForPerformCompetencyLaunch(1L, false);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> lti13Service.performLaunch(oidcIdToken, clientRegistrationId))
                .withMessageContaining("LTI is not configured for this course");
    }

    @Test
    void createUsernameFromLaunchRequest_fromUsername() {
        when(oidcIdToken.getPreferredUsername()).thenReturn("john");

        String username = lti13Service.createUsernameFromLaunchRequest(oidcIdToken, onlineCourseConfiguration);

        assertThat(username).isEqualTo("prefix_john");
    }

    @Test
    void createUsernameFromLaunchRequest_fromFullname() {
        when(oidcIdToken.getPreferredUsername()).thenReturn("");
        when(oidcIdToken.getGivenName()).thenReturn("jon");
        when(oidcIdToken.getFamilyName()).thenReturn("snow");

        String username = lti13Service.createUsernameFromLaunchRequest(oidcIdToken, onlineCourseConfiguration);

        assertThat(username).isEqualTo("prefix_jonsnow");
    }

    @Test
    void createUsernameFromLaunchRequest_fromEmailNoFamilyName() {
        when(oidcIdToken.getPreferredUsername()).thenReturn("");
        when(oidcIdToken.getGivenName()).thenReturn("john");
        when(oidcIdToken.getFamilyName()).thenReturn("");
        when(oidcIdToken.getEmail()).thenReturn("jon.snow@email.com");

        String username = lti13Service.createUsernameFromLaunchRequest(oidcIdToken, onlineCourseConfiguration);

        assertThat(username).isEqualTo("prefix_jon.snow");
    }

    @Test
    void createUsernameFromLaunchRequest_fromEmail() {
        when(oidcIdToken.getPreferredUsername()).thenReturn("");
        when(oidcIdToken.getGivenName()).thenReturn("");
        when(oidcIdToken.getFamilyName()).thenReturn("");
        when(oidcIdToken.getEmail()).thenReturn("jon.snow@email.com");

        String username = lti13Service.createUsernameFromLaunchRequest(oidcIdToken, onlineCourseConfiguration);

        assertThat(username).isEqualTo("prefix_jon.snow");
    }

    @Test
    void buildLtiResponseCallLtiService() {

        lti13Service.buildLtiResponse(UriComponentsBuilder.newInstance(), mock(HttpServletResponse.class));

        verify(ltiService).buildLtiResponse(any(), any());
    }

    @Test
    void onNewResultNoOnlineCourseConfiguration() {
        Course course = createOnlineCourse();
        Exercise exercise = new ProgrammingExercise();
        exercise.setCourse(course);
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);

        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        doReturn(null).when(onlineCourseConfigurationService).getClientRegistration(any());
        doReturn(Optional.of(ltiPlatformConfiguration)).when(ltiPlatformConfigurationRepository).findByRegistrationId(any());

        lti13Service.onNewResult(participation);

        verifyNoInteractions(resultRepository);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void onNewResultNoLaunchesForUser() {
        Course course = createOnlineCourse();
        User user = new User();
        user.setId(1L);
        Exercise exercise = new ProgrammingExercise();
        exercise.setCourse(course);
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(user);
        participation.setId(1L);

        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        doReturn(mock(ClientRegistration.class)).when(onlineCourseConfigurationService).getClientRegistration(any());
        doReturn(Collections.emptyList()).when(launchRepository).findByUserAndExercise(user, exercise);
        doReturn(Optional.of(ltiPlatformConfiguration)).when(ltiPlatformConfigurationRepository).findByRegistrationId(any());

        lti13Service.onNewResult(participation);

        verifyNoInteractions(resultRepository);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void onNewResultNoResultForUser() {
        Course course = createOnlineCourse();
        User user = new User();
        user.setId(1L);
        Exercise exercise = new ProgrammingExercise();
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
        doReturn(Optional.empty()).when(resultRepository).findFirstWithSubmissionAndFeedbacksAndTestCasesByParticipationIdOrderByCompletionDateDesc(participation.getId());
        doReturn(Optional.of(ltiPlatformConfiguration)).when(ltiPlatformConfigurationRepository).findByRegistrationId(any());

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

        Course course = createOnlineCourse();
        User user = new User();
        user.setId(1L);
        Exercise exercise = new ProgrammingExercise();
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
        doReturn(Optional.of(result)).when(resultRepository).findFirstWithSubmissionAndFeedbacksAndTestCasesByParticipationIdOrderByCompletionDateDesc(participation.getId());
        doReturn(Optional.of(ltiPlatformConfiguration)).when(ltiPlatformConfigurationRepository).findByRegistrationId(any());

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
        LtiResourceLaunch launch = state.ltiResourceLaunch;
        User user = state.user();
        Exercise exercise = state.exercise();
        StudentParticipation participation = state.participation();
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        ClientRegistration clientRegistration = state.clientRegistration();

        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        doReturn(clientRegistration).when(onlineCourseConfigurationService).getClientRegistration(any());
        doReturn(Collections.singletonList(launch)).when(launchRepository).findByUserAndExercise(user, exercise);
        doReturn(Optional.of(result)).when(resultRepository).findFirstWithSubmissionAndFeedbacksAndTestCasesByParticipationIdOrderByCompletionDateDesc(participation.getId());
        doReturn(null).when(tokenRetriever).getToken(eq(clientRegistration), eq(Scopes.AGS_SCORE));
        doReturn(Optional.of(ltiPlatformConfiguration)).when(ltiPlatformConfigurationRepository).findByRegistrationId(any());

        lti13Service.onNewResult(participation);

        verifyNoInteractions(restTemplate);
    }

    @Test
    void onNewResult() throws JsonProcessingException {
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

        LtiResourceLaunch launch = state.ltiResourceLaunch;
        User user = state.user();
        Exercise exercise = state.exercise();
        StudentParticipation participation = state.participation();
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        course.setOnlineCourse(true);
        ClientRegistration clientRegistration = state.clientRegistration();

        doReturn(Collections.singletonList(launch)).when(launchRepository).findByUserAndExercise(user, exercise);
        doReturn(Optional.of(result)).when(resultRepository).findFirstWithSubmissionAndFeedbacksAndTestCasesByParticipationIdOrderByCompletionDateDesc(participation.getId());
        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        doReturn(Optional.of(ltiPlatformConfiguration)).when(ltiPlatformConfigurationRepository).findByRegistrationId(clientRegistrationId);
        doReturn(clientRegistration).when(onlineCourseConfigurationService).getClientRegistration(any());

        String accessToken = "accessToken";
        doReturn(accessToken).when(tokenRetriever).getToken(eq(clientRegistration), eq(Scopes.AGS_SCORE));

        lti13Service.onNewResult(participation);

        ArgumentCaptor<String> urlCapture = forClass(String.class);
        var httpEntityCapture = forClass(HttpEntity.class);

        verify(restTemplate).postForEntity(urlCapture.capture(), httpEntityCapture.capture(), any());

        HttpEntity<?> capturedHttpEntity = httpEntityCapture.getValue();
        assertThat(capturedHttpEntity.getBody()).isInstanceOf(String.class);

        HttpEntity<String> httpEntity = new HttpEntity<>((String) capturedHttpEntity.getBody(), capturedHttpEntity.getHeaders());

        List<String> authHeaders = httpEntity.getHeaders().get("Authorization");
        assertThat(authHeaders).as("Score publish request must contain an Authorization header").isNotNull();
        assertThat(authHeaders).as("Score publish request must contain the corresponding Authorization Bearer token").contains("Bearer " + accessToken);

        JsonNode body = new ObjectMapper().readTree(Objects.requireNonNull(httpEntity.getBody()));
        assertThat(body.get("userId").asText()).as("Invalid parameter in score publish request: userId").isEqualTo(launch.getSub());
        assertThat(body.get("timestamp").asText()).as("Parameter missing in score publish request: timestamp").isNotNull();
        assertThat(body.get("activityProgress").asText()).as("Parameter missing in score publish request: activityProgress").isNotNull();
        assertThat(body.get("gradingProgress").asText()).as("Parameter missing in score publish request: gradingProgress").isNotNull();

        assertThat(body.get("comment").asText()).as("Invalid parameter in score publish request: comment").isEqualTo("Good job. Not so good");
        assertThat(body.get("scoreGiven").asDouble()).as("Invalid parameter in score publish request: scoreGiven").isEqualTo(scoreGiven);
        assertThat(body.get("scoreMaximum").asDouble()).as("Invalid parameter in score publish request: scoreMaximum").isEqualTo(100d);

        assertThat(launch.getScoreLineItemUrl() + "/scores").as("Score publish request was sent to a wrong URI").isEqualTo(urlCapture.getValue());

    }

    @Test
    void startDeepLinkingLtiConfigurationFound() {
        when(oidcIdToken.getEmail()).thenReturn("testuser@email.com");

        doReturn(Optional.of(ltiPlatformConfiguration)).when(ltiPlatformConfigurationRepository).findByRegistrationId(clientRegistrationId);
        Optional<User> user = Optional.of(new User());
        doReturn(user).when(userRepository).findOneWithGroupsAndAuthoritiesByLogin(any());
        doNothing().when(ltiService).authenticateLtiUser(any(), any(), any(), any(), anyBoolean());
        doNothing().when(ltiService).onSuccessfulLtiAuthentication(any(), any());

        lti13Service.startDeepLinking(oidcIdToken, clientRegistrationId);
    }

    @Test
    void startDeepLinkingPlatformNotFound() {
        when(oidcIdToken.getClaim(Claims.TARGET_LINK_URI)).thenReturn("https://some-artemis-domain.org/lti/deep-linking");
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> lti13Service.startDeepLinking(oidcIdToken, clientRegistrationId));
    }

    @Test
    void getCourseFromTargetLink_validCoursePath() {
        String targetLinkUrl = "https://some-artemis-domain.org/courses/123";
        Course mockCourse = new Course();
        mockCourse.setId(123L);

        when(courseRepository.findById(123L)).thenReturn(Optional.of(mockCourse));

        Optional<Course> course = lti13Service.getCourseFromTargetLink(targetLinkUrl);

        assertThat(course).isPresent();
        assertThat(course.get().getId()).isEqualTo(123L);
        verify(courseRepository).findById(123L);
    }

    @Test
    void getCourseFromTargetLink_malformedUrl() {
        String targetLinkUrl = "invalid-url";

        Optional<Course> course = lti13Service.getCourseFromTargetLink(targetLinkUrl);

        assertThat(course).isEmpty();
        verifyNoInteractions(courseRepository);
    }

    @Test
    void getCourseFromTargetLink_missingCourseId() {
        String targetLinkUrl = "https://some-artemis-domain.org/with/invalid/path";

        Optional<Course> course = lti13Service.getCourseFromTargetLink(targetLinkUrl);

        assertThat(course).isEmpty();
        verifyNoInteractions(courseRepository);
    }

    @Test
    void getCourseFromTargetLink_noMatchingPath() {
        String targetLinkUrl = "https://some-artemis-domain.org/lectures/567";

        Optional<Course> course = lti13Service.getCourseFromTargetLink(targetLinkUrl);

        assertThat(course).isEmpty();
        verifyNoInteractions(courseRepository);
    }

    @Test
    void getCourseFromTargetLink_validCompetencyPath() {
        String targetLinkUrl = "https://some-artemis-domain.org/courses/456/competencies";
        Course mockCourse = new Course();
        mockCourse.setId(456L);

        when(courseRepository.findById(456L)).thenReturn(Optional.of(mockCourse));

        Optional<Course> course = lti13Service.getCourseFromTargetLink(targetLinkUrl);

        assertThat(course).isPresent();
        assertThat(course.get().getId()).isEqualTo(456L);
        verify(courseRepository).findById(456L);
    }

    @Test
    void getTargetLinkType_competencyPath() {
        String targetLinkUrl = "https://some-artemis-domain.org/courses/123/competencies";

        DeepLinkingType linkType = lti13Service.getTargetLinkType(targetLinkUrl);

        assertThat(linkType).isEqualTo(DeepLinkingType.COMPETENCY);
    }

    @Test
    void getTargetLinkType_irisPath() {
        String targetLinkUrl = "https://some-artemis-domain.org/courses/123/dashboard";

        DeepLinkingType linkType = lti13Service.getTargetLinkType(targetLinkUrl);

        assertThat(linkType).isEqualTo(DeepLinkingType.IRIS);
    }

    @Test
    void getTargetLinkType_learningPath() {
        String targetLinkUrl = "https://some-artemis-domain.org/courses/123/learning-path";

        DeepLinkingType linkType = lti13Service.getTargetLinkType(targetLinkUrl);

        assertThat(linkType).isEqualTo(DeepLinkingType.LEARNING_PATH);
    }

    @Test
    void getTargetLinkType_unknownPath() {
        String targetLinkUrl = "https://some-artemis-domain.org/courses/123/invalid-path";

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> lti13Service.getTargetLinkType(targetLinkUrl)).withMessage("Content type not found");
    }

    @Test
    void getLectureFromTargetLink_validLecturePath() {
        String targetLinkUrl = "https://some-artemis-domain.org/courses/123/lectures/456";
        Lecture mockLecture = new Lecture();
        mockLecture.setId(456L);

        when(lectureRepository.findById(456L)).thenReturn(Optional.of(mockLecture));

        Optional<Lecture> lecture = lti13Service.getLectureFromTargetLink(targetLinkUrl);

        assertThat(lecture).isPresent();
        assertThat(lecture.get().getId()).isEqualTo(456L);
        verify(lectureRepository).findById(456L);
    }

    @Test
    void buildLtiEmailInUseResponse_emailInUse() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        OidcIdToken ltiIdToken = mock(OidcIdToken.class);
        String email = "testuser@email.com";
        String username = "testuser";

        when(ltiIdToken.getEmail()).thenReturn(email);
        when(artemisAuthenticationProvider.getUsernameForEmail(email)).thenReturn(Optional.of(username));

        lti13Service.buildLtiEmailInUseResponse(response, ltiIdToken);

        verify(response).addHeader("ltiSuccessLoginRequired", username);
        verify(ltiService).prepareLogoutCookie(response);
    }

    @Test
    void buildLtiEmailInUseResponse_emailNotInUse() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        OidcIdToken ltiIdToken = mock(OidcIdToken.class);
        String email = "testuser@email.com";

        when(ltiIdToken.getEmail()).thenReturn(email);
        when(artemisAuthenticationProvider.getUsernameForEmail(email)).thenReturn(Optional.empty());

        lti13Service.buildLtiEmailInUseResponse(response, ltiIdToken);

        verify(response, never()).addHeader(eq("ltiSuccessLoginRequired"), anyString());
        verify(ltiService).prepareLogoutCookie(response);
    }

    @Test
    void hasTargetLinkWithoutExercise_competencyPath() {
        String targetLinkUrl = "https://some-artemis-domain.org/courses/123/competencies";
        Optional<Lecture> targetLecture = Optional.empty();

        boolean result = lti13Service.hasTargetLinkWithoutExercise(targetLinkUrl, targetLecture);

        assertThat(result).isTrue();
    }

    @Test
    void hasTargetLinkWithoutExercise_learningPath() {
        String targetLinkUrl = "https://some-artemis-domain.org/courses/123/learning-path";
        Optional<Lecture> targetLecture = Optional.empty();

        boolean result = lti13Service.hasTargetLinkWithoutExercise(targetLinkUrl, targetLecture);

        assertThat(result).isTrue();
    }

    @Test
    void hasTargetLinkWithoutExercise_irisPath() {
        String targetLinkUrl = "https://some-artemis-domain.org/courses/123/dashboard";
        Optional<Lecture> targetLecture = Optional.empty();

        boolean result = lti13Service.hasTargetLinkWithoutExercise(targetLinkUrl, targetLecture);

        assertThat(result).isTrue();
    }

    private State getValidStateForNewResult(Result result) {
        User user = new User();
        user.setLogin("someone");

        Course course = createOnlineCourse();

        Exercise exercise = new ProgrammingExercise();
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
    private record State(LtiResourceLaunch ltiResourceLaunch, Exercise exercise, User user, StudentParticipation participation, Result result,
            ClientRegistration clientRegistration) {
    }

    private MockExercise getMockExercise(boolean isOnlineCourse) {
        long exerciseId = 134;
        Exercise exercise = new TextExercise();
        exercise.setId(exerciseId);

        long courseId = 12;
        Course course = new Course();
        course.setId(courseId);
        if (isOnlineCourse) {
            course.setOnlineCourseConfiguration(new OnlineCourseConfiguration());
        }
        exercise.setCourse(course);
        doReturn(Optional.of(exercise)).when(exerciseRepository).findById(exerciseId);
        doReturn(course).when(courseRepository).findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);
        return new MockExercise(exerciseId, courseId);
    }

    private Course getMockCourse(long courseId) {
        Course course = new Course();
        course.setId(courseId);
        course.setOnlineCourseConfiguration(new OnlineCourseConfiguration());

        return course;
    }

    private record MockExercise(long exerciseId, long courseId) {
    }

    private void prepareForPerformExerciseLaunch(long courseId, long exerciseId, boolean isOnlineCourse) {
        this.prepareForPerformLaunch(courseId, "https://some-artemis-domain.org/courses/" + courseId + "/exercises/" + exerciseId, isOnlineCourse);
    }

    private void prepareForPerformCompetencyLaunch(long courseId, boolean isOnlineCourse) {
        this.prepareForPerformLaunch(courseId, "https://some-artemis-domain.org/courses/" + courseId + "/competencies", isOnlineCourse);
    }

    private void prepareForPerformLaunch(long courseId, String targetLinkUri, boolean isOnlineCourse) {
        when(oidcIdToken.getEmail()).thenReturn("testuser@email.com");

        when(oidcIdToken.getClaim(Claims.TARGET_LINK_URI)).thenReturn(targetLinkUri);

        Optional<User> user = Optional.of(new User());
        doReturn(user).when(userRepository).findOneWithGroupsAndAuthoritiesByLogin(any());

        if (isOnlineCourse) {
            doReturn(Optional.of(getMockCourse(courseId))).when(courseRepository).findById(courseId);
            doReturn(getMockCourse(courseId)).when(courseRepository).findWithEagerOnlineCourseConfigurationById(courseId);
        }
        else {
            Course course = getMockCourse(courseId);
            course.setOnlineCourseConfiguration(null);
            doReturn(Optional.of(course)).when(courseRepository).findById(courseId);
            doReturn(course).when(courseRepository).findWithEagerOnlineCourseConfigurationById(courseId);
        }

        doNothing().when(ltiService).authenticateLtiUser(any(), any(), any(), any(), anyBoolean());
        doNothing().when(ltiService).onSuccessfulLtiAuthentication(any(), any());
    }

    private Course createOnlineCourse() {
        Course course = new Course();
        course.setId(1L);
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setLtiPlatformConfiguration(ltiPlatformConfiguration);
        onlineCourseConfiguration.setCourse(course);
        course.setOnlineCourseConfiguration(onlineCourseConfiguration);
        return course;
    }
}
