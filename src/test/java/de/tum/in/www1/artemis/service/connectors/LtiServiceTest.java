package de.tum.in.www1.artemis.service.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.SecurityUtils;
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
    private TokenProvider tokenProvider;

    private Exercise exercise;

    private LtiService ltiService;

    private Course course;

    private OnlineCourseConfiguration onlineCourseConfiguration;

    private User user;

    private final String courseStudentGroupName = "courseStudentGroupName";

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        ltiService = new LtiService(userCreationService, userRepository, artemisAuthenticationProvider, tokenProvider);
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
    }

    @Test
    void addLtiQueryParamsNewUser() {
        when(userRepository.getUser()).thenReturn(user);
        user.setActivated(false);
        when(tokenProvider.createToken(any(), anyBoolean())).thenReturn("testJwt");

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.newInstance();

        ltiService.addLtiQueryParams(uriComponentsBuilder);

        UriComponents uriComponents = uriComponentsBuilder.build();

        String jwt = uriComponents.getQueryParams().getFirst("jwt");
        String initialize = uriComponents.getQueryParams().getFirst("initialize");
        String ltiSuccessLoginRequired = uriComponents.getQueryParams().getFirst("ltiSuccessLoginRequired");
        assertEquals("testJwt", jwt);
        assertEquals("", initialize);
        assertNull(ltiSuccessLoginRequired);
    }

    @Test
    void addLtiQueryParamsExistingUser() {
        when(userRepository.getUser()).thenReturn(user);
        user.setActivated(true);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.newInstance();

        ltiService.addLtiQueryParams(uriComponentsBuilder);

        UriComponents uriComponents = uriComponentsBuilder.build();

        String jwt = uriComponents.getQueryParams().getFirst("jwt");
        String initialize = uriComponents.getQueryParams().getFirst("initialize");
        String ltiSuccessLoginRequired = uriComponents.getQueryParams().getFirst("ltiSuccessLoginRequired");
        assertEquals("", jwt);
        assertEquals(user.getLogin(), ltiSuccessLoginRequired);
        assertNull(initialize);
    }

    @Test
    void successFullAuthentication() {
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        ltiService.onSuccessfulLtiAuthentication(user, exercise);

        assertThat(user.getGroups()).contains(courseStudentGroupName);
        assertThat(user.getGroups()).contains(LtiService.LTI_GROUP_NAME);

        verify(userCreationService, times(1)).saveUser(user);
        verify(artemisAuthenticationProvider, times(1)).addUserToGroup(user, courseStudentGroupName);
    }

    @Test
    void authenticateLtiUser_AlreadyAuthenticated() {
        Authentication auth = SecurityUtils.makeAuthorizationObject("student1");
        SecurityContextHolder.getContext().setAuthentication(auth);

        ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", false, false);

        assertEquals(auth, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void authenticateLtiUser_noEmail() {
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThrows(InternalAuthenticationServiceException.class, () -> ltiService.authenticateLtiUser("", "username", "firstname", "lastname", false, false));
    }

    @Test
    void authenticateLtiUser_lookupUserByEmail() {
        SecurityContextHolder.getContext().setAuthentication(null);

        when(userRepository.findOneByLogin("username")).thenReturn(Optional.empty());
        when(userCreationService.createUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(user);

        ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", false, true);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(user.getLogin(), auth.getPrincipal());
    }

    @Test
    void authenticateLtiUser_newUser() {
        SecurityContextHolder.getContext().setAuthentication(null);

        when(artemisAuthenticationProvider.getUsernameForEmail("email")).thenReturn(Optional.of("username"));
        when(artemisAuthenticationProvider.getOrCreateUser(any(), any(), any(), any(), anyBoolean())).thenReturn(user);

        ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", false, true);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(user.getLogin(), auth.getPrincipal());
    }

    @Test
    void authenticateLtiUser_noAuth() {
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThrows(InternalAuthenticationServiceException.class, () -> ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", true, false));
    }

    @Test
    void isLtiCreatedUser() {
        assertTrue(ltiService.isLtiCreatedUser(user));
    }

    @Test
    void isNotLtiCreatedUser() {
        user.setGroups(new HashSet<>(Arrays.asList("students", "editors")));

        assertFalse(ltiService.isLtiCreatedUser(user));
    }
}
