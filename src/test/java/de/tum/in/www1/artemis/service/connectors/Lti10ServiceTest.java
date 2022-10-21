package de.tum.in.www1.artemis.service.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private LtiService ltiService;

    private Exercise exercise;

    private Lti10Service lti10Service;

    private Course course;

    private OnlineCourseConfiguration onlineCourseConfiguration;

    private LtiLaunchRequestDTO launchRequest;

    private User user;

    private LtiUserId ltiUserId;

    private final String courseStudentGroupName = "courseStudentGroupName";

    private LtiOutcomeUrl ltiOutcomeUrl;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        lti10Service = new Lti10Service(userRepository, ltiOutcomeUrlRepository, resultRepository, courseRepository, ltiService);
        course = new Course();
        course.setId(100L);
        course.setStudentGroupName(courseStudentGroupName);
        course.setOnlineCourse(true);
        onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setCourse(course);
        course.setOnlineCourseConfiguration(onlineCourseConfiguration);
        exercise = new TextExercise();
        exercise.setCourse(course);
        launchRequest = AuthenticationIntegrationTestHelper.setupDefaultLtiLaunchRequest();
        user = new User();
        user.setLogin("login");
        user.setPassword("password");
        user.setGroups(new HashSet<>(Collections.singleton(LtiService.LTI_GROUP_NAME)));
        ltiUserId = new LtiUserId();
        ltiUserId.setUser(user);
        ltiOutcomeUrl = new LtiOutcomeUrl();
    }

    @Test
    void performLaunch() {
        doNothing().when(ltiService).authenticateLtiUser(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);
        doNothing().when(ltiService).onSuccessfulLtiAuthentication(any(), any(), any());

        lti10Service.performLaunch(launchRequest, exercise, onlineCourseConfiguration);

        verify(ltiOutcomeUrlRepository, times(1)).findByUserAndExercise(user, exercise);
        verify(ltiOutcomeUrlRepository, times(1)).save(any());
    }

    @Test
    void verifyRequest_onlineCourseConfigurationNotSpecified() {
        onlineCourseConfiguration = null;

        HttpServletRequest request = mock(HttpServletRequest.class);
        String message = lti10Service.verifyRequest(request, onlineCourseConfiguration);
        assertThat("verifyRequest for LTI is not supported for this course").isEqualTo(message);
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
    void onNewResult() {
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

        lti10Service.onNewResult(participation);
        verify(resultRepository, times(1)).findFirstByParticipationIdOrderByCompletionDateDesc(27L);
        verify(courseRepository, times(1)).findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
    }

    private void ltiOutcomeUrlRepositorySetup(User user) {
        when(ltiOutcomeUrlRepository.findByUserAndExercise(user, exercise)).thenReturn(Optional.of(ltiOutcomeUrl));
    }
}
