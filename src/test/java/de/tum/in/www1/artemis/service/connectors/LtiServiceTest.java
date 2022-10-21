package de.tum.in.www1.artemis.service.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.LtiUserIdRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.user.UserCreationService;

class LtiServiceTest {

    @Mock
    private UserCreationService userCreationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ArtemisAuthenticationProvider artemisAuthenticationProvider;

    @Mock
    private LtiUserIdRepository ltiUserIdRepository;

    @Mock
    private TokenProvider tokenProvider;

    private Exercise exercise;

    private LtiService ltiService;

    private Course course;

    private OnlineCourseConfiguration onlineCourseConfiguration;

    private User user;

    private LtiUserId ltiUserId;

    private final String courseStudentGroupName = "courseStudentGroupName";

    private LtiOutcomeUrl ltiOutcomeUrl;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        ltiService = new LtiService(userCreationService, userRepository, artemisAuthenticationProvider, tokenProvider, ltiUserIdRepository);
        course = new Course();
        course.setId(100L);
        course.setStudentGroupName(courseStudentGroupName);
        course.setOnlineCourse(true);
        onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setCourse(course);
        course.setOnlineCourseConfiguration(onlineCourseConfiguration);
        exercise = new TextExercise();
        exercise.setCourse(course);
        user = new User();
        user.setLogin("login");
        user.setPassword("password");
        user.setGroups(new HashSet<>(Collections.singleton(LtiService.LTI_GROUP_NAME)));
        ltiUserId = new LtiUserId();
        ltiUserId.setUser(user);
        ltiOutcomeUrl = new LtiOutcomeUrl();
    }

    private void onSuccessfulAuthenticationSetup(User user, LtiUserId ltiUserId) {
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);
        when(ltiUserIdRepository.findByUser(user)).thenReturn(Optional.of(ltiUserId));
    }

    private void onSuccessfulAuthenticationAssertions(User user, LtiUserId ltiUserId) {
        assertThat(user.getGroups()).contains(courseStudentGroupName);
        assertThat(user.getGroups()).contains(LtiService.LTI_GROUP_NAME);
        assertThat("ff30145d6884eeb2c1cef50298939383").isEqualTo(ltiUserId.getLtiUserId());
        assertThat("some.outcome.service.url.com").isEqualTo(ltiOutcomeUrl.getUrl());
        assertThat("someResultSourceId").isEqualTo(ltiOutcomeUrl.getSourcedId());
        verify(userCreationService, times(1)).saveUser(user);
        verify(artemisAuthenticationProvider, times(1)).addUserToGroup(user, courseStudentGroupName);
    }
}
