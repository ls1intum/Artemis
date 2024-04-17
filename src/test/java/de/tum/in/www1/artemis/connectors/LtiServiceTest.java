package de.tum.in.www1.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.jwt.JWTCookieService;
import de.tum.in.www1.artemis.service.connectors.ci.CIUserManagementService;
import de.tum.in.www1.artemis.service.connectors.lti.LtiService;
import de.tum.in.www1.artemis.service.connectors.vcs.VcsUserManagementService;
import de.tum.in.www1.artemis.service.user.UserCreationService;

class LtiServiceTest {

    @Mock
    private UserCreationService userCreationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ArtemisAuthenticationProvider artemisAuthenticationProvider;

    @Mock
    private JWTCookieService jwtCookieService;

    @Mock
    private Optional<VcsUserManagementService> optionalVcsUserManagementService;

    @Mock
    private Optional<CIUserManagementService> optionalCIUserManagementService;

    private Exercise exercise;

    private LtiService ltiService;

    private OnlineCourseConfiguration onlineCourseConfiguration;

    private User user;

    private final String courseStudentGroupName = "courseStudentGroupName";

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        ltiService = new LtiService(userCreationService, userRepository, artemisAuthenticationProvider, jwtCookieService, optionalVcsUserManagementService,
                optionalCIUserManagementService);
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
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(userCreationService, userRepository, artemisAuthenticationProvider, jwtCookieService);
    }

    @Test
    void addLtiQueryParamsNewUser() {
        when(userRepository.getUser()).thenReturn(user);
        user.setActivated(false);
        when(jwtCookieService.buildLoginCookie(true)).thenReturn(mock(ResponseCookie.class));

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.newInstance();
        HttpServletResponse response = mock(HttpServletResponse.class);

        ltiService.buildLtiResponse(uriComponentsBuilder, response);

        UriComponents uriComponents = uriComponentsBuilder.build();

        verify(jwtCookieService).buildLoginCookie(true);
        verify(response).addHeader(any(), any());

        String initialize = uriComponents.getQueryParams().getFirst("initialize");
        String ltiSuccessLoginRequired = uriComponents.getQueryParams().getFirst("ltiSuccessLoginRequired");
        assertThat(initialize).isEmpty();
        assertThat(ltiSuccessLoginRequired).isNull();
    }

    @Test
    void addLtiQueryParamsExistingUser() {
        when(userRepository.getUser()).thenReturn(user);
        user.setActivated(true);
        when(jwtCookieService.buildLogoutCookie()).thenReturn(mock(ResponseCookie.class));

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.newInstance();
        HttpServletResponse response = mock(HttpServletResponse.class);

        ltiService.buildLtiResponse(uriComponentsBuilder, response);

        UriComponents uriComponents = uriComponentsBuilder.build();

        verify(jwtCookieService).buildLogoutCookie();
        verify(response).addHeader(any(), any());

        String initialize = uriComponents.getQueryParams().getFirst("initialize");
        String ltiSuccessLoginRequired = uriComponents.getQueryParams().getFirst("ltiSuccessLoginRequired");
        assertThat(ltiSuccessLoginRequired).isEqualTo(user.getLogin());
        assertThat(initialize).isNull();
    }

    @Test
    void successFullAuthentication() {
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        ltiService.onSuccessfulLtiAuthentication(user, exercise);

        assertThat(user.getGroups()).contains(courseStudentGroupName);
        assertThat(user.getGroups()).contains(LtiService.LTI_GROUP_NAME);

        verify(userCreationService).saveUser(user);
    }

    @Test
    void authenticateLtiUser_AlreadyAuthenticatedSameEmail() {
        Authentication auth = SecurityUtils.makeAuthorizationObject("student1");
        SecurityContextHolder.getContext().setAuthentication(auth);
        user.setEmail("useremail@tum.de");
        when(userRepository.getUser()).thenReturn(user);

        ltiService.authenticateLtiUser("useremail@tum.de", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(auth);
    }

    @Test
    void authenticateLtiUser_AlreadyAuthenticatedDifferentEmail() {
        Authentication auth = SecurityUtils.makeAuthorizationObject("user");
        SecurityContextHolder.getContext().setAuthentication(auth);
        user.setEmail("useremail@tum.de");
        when(userRepository.getUser()).thenReturn(user);
        when(userRepository.findOneByLogin("username")).thenReturn(Optional.empty());
        when(userCreationService.createUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(user);

        ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser());

        auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo(user.getLogin());
    }

    @Test
    void authenticateLtiUser_noEmail() {
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThatExceptionOfType(InternalAuthenticationServiceException.class)
                .isThrownBy(() -> ltiService.authenticateLtiUser("", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser()));
    }

    @Test
    void authenticateLtiUser_lookupUserByEmail() {
        SecurityContextHolder.getContext().setAuthentication(null);

        when(userRepository.findOneByLogin("username")).thenReturn(Optional.empty());
        when(userCreationService.createUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(user);

        ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo(user.getLogin());
    }

    @Test
    void authenticateLtiUser_newUser() {
        SecurityContextHolder.getContext().setAuthentication(null);

        when(artemisAuthenticationProvider.getUsernameForEmail("email")).thenReturn(Optional.of("username"));
        when(userRepository.findOneByEmailIgnoreCase("email")).thenReturn(Optional.ofNullable(user));

        assertThatExceptionOfType(InternalAuthenticationServiceException.class)
                .isThrownBy(() -> ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser()));
    }

    @Test
    void authenticateLtiUser_noAuth() {
        SecurityContextHolder.getContext().setAuthentication(null);
        onlineCourseConfiguration.setRequireExistingUser(true);

        assertThatExceptionOfType(InternalAuthenticationServiceException.class)
                .isThrownBy(() -> ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser()));
    }

    @Test
    void isLtiCreatedUser() {
        assertThat(ltiService.isLtiCreatedUser(user)).isTrue();
    }

    @Test
    void isNotLtiCreatedUser() {
        user.setGroups(new HashSet<>(Arrays.asList("students", "editors")));

        assertThat(ltiService.isLtiCreatedUser(user)).isFalse();
    }
}
