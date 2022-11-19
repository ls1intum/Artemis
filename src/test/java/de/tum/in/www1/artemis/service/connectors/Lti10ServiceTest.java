package de.tum.in.www1.artemis.service.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.authentication.AuthenticationIntegrationTestHelper;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;

class Lti10ServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private HttpClient client;

    @Mock
    private LtiService ltiService;

    private Exercise exercise;

    private Lti10Service lti10Service;

    private Course course;

    private OnlineCourseConfiguration onlineCourseConfiguration;

    private LtiLaunchRequestDTO launchRequest;

    private User user;

    private LtiOutcomeUrl ltiOutcomeUrl;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        lti10Service = new Lti10Service(userRepository, ltiOutcomeUrlRepository, resultRepository, courseRepository, ltiService);
        ReflectionTestUtils.setField(lti10Service, "client", client);

        course = new Course();
        course.setId(100L);
        course.setOnlineCourse(true);
        onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setCourse(course);
        onlineCourseConfiguration.setLtiKey("key");
        onlineCourseConfiguration.setLtiSecret("secret");
        onlineCourseConfiguration.setUserPrefix("prefix");
        course.setOnlineCourseConfiguration(onlineCourseConfiguration);
        exercise = new TextExercise();
        exercise.setCourse(course);
        launchRequest = AuthenticationIntegrationTestHelper.setupDefaultLtiLaunchRequest();
        user = new User();
        user.setLogin("login");
        user.setPassword("password");
        user.setGroups(new HashSet<>(Collections.singleton(LtiService.LTI_GROUP_NAME)));
        ltiOutcomeUrl = new LtiOutcomeUrl();
        ltiOutcomeUrl.setUrl("http://testUrl.com");
    }

    @Test
    void performLaunch() {
        doNothing().when(ltiService).authenticateLtiUser(any(), any(), any(), any(), anyBoolean());
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);
        doNothing().when(ltiService).onSuccessfulLtiAuthentication(any(), any());

        lti10Service.performLaunch(launchRequest, exercise, onlineCourseConfiguration);

        verify(ltiOutcomeUrlRepository, times(1)).findByUserAndExercise(user, exercise);
        verify(ltiOutcomeUrlRepository, times(1)).save(any());
    }

    @Test
    void performLaunchNoOutcomeUrl() {
        doNothing().when(ltiService).authenticateLtiUser(any(), any(), any(), any(), anyBoolean());
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);
        doNothing().when(ltiService).onSuccessfulLtiAuthentication(any(), any());

        launchRequest.setLis_outcome_service_url("");

        lti10Service.performLaunch(launchRequest, exercise, onlineCourseConfiguration);

        verifyNoInteractions(ltiOutcomeUrlRepository);
    }

    @Test
    void verifyRequest_onlineCourseConfigurationNotSpecified() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String message = lti10Service.verifyRequest(request, null);
        assertThat("verifyRequest for LTI is not supported for this course").isEqualTo(message);
    }

    @Test
    void createUsernameFromLaunchRequest_fromUsername() {
        launchRequest.setExt_user_username("john");

        String username = lti10Service.createUsernameFromLaunchRequest(launchRequest, onlineCourseConfiguration);

        assertEquals("prefix_john", username);
    }

    @Test
    void createUsernameFromLaunchRequest_fromPersonId() {
        launchRequest.setExt_user_username("");
        launchRequest.setLis_person_sourcedid("johnid");

        String username = lti10Service.createUsernameFromLaunchRequest(launchRequest, onlineCourseConfiguration);

        assertEquals("prefix_johnid", username);
    }

    @Test
    void createUsernameFromLaunchRequest_fromUserId() {
        launchRequest.setExt_user_username("");
        launchRequest.setLis_person_sourcedid("");
        launchRequest.setUser_id("userid");

        String username = lti10Service.createUsernameFromLaunchRequest(launchRequest, onlineCourseConfiguration);

        assertEquals("prefix_userid", username);
    }

    @Test
    void createUsernameFromLaunchRequest_fromEmail() {
        launchRequest.setExt_user_username("");
        launchRequest.setLis_person_sourcedid("");
        launchRequest.setUser_id("");
        launchRequest.setLis_person_contact_email_primary("jon.snow@email.com");

        String username = lti10Service.createUsernameFromLaunchRequest(launchRequest, onlineCourseConfiguration);

        assertEquals("prefix_jon.snow", username);
    }

    @Test
    void getUserLastNameFromLaunchRequest_fromFamilyName() {
        launchRequest.setLis_person_name_family("snow");

        String lastname = lti10Service.getUserLastNameFromLaunchRequest(launchRequest);

        assertEquals("snow", lastname);
    }

    @Test
    void getUserLastNameFromLaunchRequest_fromSourceId() {
        launchRequest.setLis_person_name_family("");
        launchRequest.setLis_person_sourcedid("sourceId");

        String lastname = lti10Service.getUserLastNameFromLaunchRequest(launchRequest);

        assertEquals("sourceId", lastname);
    }

    @Test
    void getUserLastNameFromLaunchRequest_empty() {
        launchRequest.setLis_person_name_family("");
        launchRequest.setLis_person_sourcedid("");

        String lastname = lti10Service.getUserLastNameFromLaunchRequest(launchRequest);

        assertEquals("", lastname);
    }

    @Test
    void verifyRequest_unsuccessfulVerification() {
        onlineCourseConfiguration.setLtiSecret("secret");

        String url = "https://some.url.com";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI(url);
        String message = lti10Service.verifyRequest(request, onlineCourseConfiguration);
        assertThat("LTI signature verification failed with message: Failed to validate: parameter_absent; error: bad_request, launch result: null").isEqualTo(message);
    }

    @Test
    void onNewResultNoOnlinecCourseConfiguration() {
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        course.setOnlineCourseConfiguration(null);
        when(courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId())).thenReturn(course);

        assertThrows(IllegalStateException.class, () -> lti10Service.onNewResult(participation));

        verifyNoInteractions(resultRepository);
    }

    @Test
    void onNewResult_invalidUrl() {
        onlineCourseConfiguration.setLtiKey("oauthKey");
        onlineCourseConfiguration.setLtiSecret("oauthSecret");
        ltiOutcomeUrl.setUrl("invalid");

        StudentParticipation participation = new StudentParticipation();
        User user = new User();
        participation.setParticipant(user);
        participation.setExercise(exercise);
        participation.setId(27L);
        Result result = new Result();
        result.setScore(3D);
        ltiOutcomeUrlRepositorySetup(user);
        when(resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(27L)).thenReturn(Optional.of(result));
        when(courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId())).thenReturn(course);

        lti10Service.onNewResult(participation);
        verify(resultRepository, times(1)).findFirstByParticipationIdOrderByCompletionDateDesc(27L);
        verify(courseRepository, times(1)).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        verifyNoInteractions(client);
    }

    @Test
    void onNewResult() throws IOException {
        onlineCourseConfiguration.setLtiKey("oauthKey");
        onlineCourseConfiguration.setLtiSecret("oauthSecret");

        StudentParticipation participation = new StudentParticipation();
        User user = new User();
        participation.setParticipant(user);
        participation.setExercise(exercise);
        participation.setId(27L);
        Result result = new Result();
        result.setScore(3D);
        ltiOutcomeUrlRepositorySetup(user);
        when(resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(27L)).thenReturn(Optional.of(result));
        when(courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId())).thenReturn(course);
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(client.execute(any())).thenReturn(response);

        lti10Service.onNewResult(participation);
        verify(resultRepository, times(1)).findFirstByParticipationIdOrderByCompletionDateDesc(27L);
        verify(courseRepository, times(1)).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        verify(client, times(1)).execute(any());
    }

    private void ltiOutcomeUrlRepositorySetup(User user) {
        when(ltiOutcomeUrlRepository.findByUserAndExercise(user, exercise)).thenReturn(Optional.of(ltiOutcomeUrl));
    }
}
