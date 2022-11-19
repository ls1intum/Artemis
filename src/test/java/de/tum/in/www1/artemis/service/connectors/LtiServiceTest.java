package de.tum.in.www1.artemis.service.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
import de.tum.in.www1.artemis.repository.LtiUserIdRepository;
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
    private LtiUserIdRepository ltiUserIdRepository;

    @Mock
    private TokenProvider tokenProvider;

    private Exercise exercise;

    private LtiService ltiService;

    private OnlineCourseConfiguration onlineCourseConfiguration;

    private User user;

    private LtiUserId ltiUserId;

    private final String courseStudentGroupName = "courseStudentGroupName";

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        ltiService = new LtiService(userCreationService, userRepository, artemisAuthenticationProvider, tokenProvider, ltiUserIdRepository);
        Course course = new Course();
        course.setId(100L);
        course.setStudentGroupName(courseStudentGroupName);
        onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setCourse(course);
        exercise = new TextExercise();
        exercise.setCourse(course);
        user = new User();
        user.setLogin("login");
        user.setPassword("password");
        user.setGroups(new HashSet<>(Collections.singleton(LtiService.LTI_GROUP_NAME)));
        ltiUserId = new LtiUserId();
        ltiUserId.setLtiUserId("ltiUserId");
        ltiUserId.setUser(user);
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
        when(ltiUserIdRepository.findByUser(user)).thenReturn(Optional.of(ltiUserId));

        ltiService.onSuccessfulLtiAuthentication(user, "ltiUserId", exercise);

        assertThat(user.getGroups()).contains(courseStudentGroupName);
        assertThat(user.getGroups()).contains(LtiService.LTI_GROUP_NAME);
        assertEquals(ltiUserId.getLtiUserId(), "ltiUserId");

        verify(userCreationService, times(1)).saveUser(user);
        verify(artemisAuthenticationProvider, times(1)).addUserToGroup(user, courseStudentGroupName);
    }

    @Test
    void authenticateLtiUser_AlreadyAuthenticatedSameEmail() {
        Authentication auth = SecurityUtils.makeAuthorizationObject("student1");
        SecurityContextHolder.getContext().setAuthentication(auth);
        user.setEmail("useremail@tum.de");
        when(userRepository.getUser()).thenReturn(user);

        ltiService.authenticateLtiUser("useremail@tum.de", "userid", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser());

        assertEquals(auth, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void authenticateLtiUser_AlreadyAuthenticatedDifferentEmail() {
        Authentication auth = SecurityUtils.makeAuthorizationObject("user");
        SecurityContextHolder.getContext().setAuthentication(auth);
        user.setEmail("useremail@tum.de");
        when(userRepository.getUser()).thenReturn(user);
        User ltiUser = new User();
        ltiUser.setEmail("email");
        ltiUser.setLogin("ltiUser");
        ltiUserId.setUser(ltiUser);
        when(ltiUserIdRepository.findByLtiUserId(ltiUserId.getLtiUserId())).thenReturn(Optional.of(ltiUserId));

        ltiService.authenticateLtiUser("email", ltiUserId.getLtiUserId(), "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser());

        auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(auth.getPrincipal(), ltiUser.getLogin());
    }

    @Test
    void authenticateLtiUser_noEmail() {
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThrows(InternalAuthenticationServiceException.class,
                () -> ltiService.authenticateLtiUser("", "userid", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser()));
    }

    @Test
    void authenticateLtiUser_existingLtiUser() {
        SecurityContextHolder.getContext().setAuthentication(null);

        when(ltiUserIdRepository.findByLtiUserId(ltiUserId.getLtiUserId())).thenReturn(Optional.of(ltiUserId));

        ltiService.authenticateLtiUser("email", ltiUserId.getLtiUserId(), "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(user.getLogin(), auth.getPrincipal());
    }

    @Test
    void authenticateLtiUser_lookupUserByEmail() {
        SecurityContextHolder.getContext().setAuthentication(null);

        when(userRepository.findOneByLogin("username")).thenReturn(Optional.empty());
        when(userCreationService.createUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(user);

        ltiService.authenticateLtiUser("email", "ltiUserId", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(user.getLogin(), auth.getPrincipal());
    }

    @Test
    void authenticateLtiUser_newUser() {
        SecurityContextHolder.getContext().setAuthentication(null);

        when(artemisAuthenticationProvider.getUsernameForEmail("email")).thenReturn(Optional.of("username"));
        when(artemisAuthenticationProvider.getOrCreateUser(any(), any(), any(), any(), anyBoolean())).thenReturn(user);

        ltiService.authenticateLtiUser("email", "ltiUserId", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(user.getLogin(), auth.getPrincipal());
        assertThat(user.getGroups().contains(LtiService.LTI_GROUP_NAME));
    }

    @Test
    void authenticateLtiUser_noAuth() {
        SecurityContextHolder.getContext().setAuthentication(null);
        onlineCourseConfiguration.setRequireExistingUser(true);

        assertThrows(InternalAuthenticationServiceException.class,
                () -> ltiService.authenticateLtiUser("email", "ltiUserId", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser()));
    }
}
