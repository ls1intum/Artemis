package de.tum.cit.aet.artemis.lti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.security.ArtemisAuthenticationProvider;
import de.tum.cit.aet.artemis.account.service.user.AuthorityService;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.test_repository.UserCourseRoleTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.lti.service.LtiService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class LtiServiceTest {

    @Mock
    private UserCreationService userCreationService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private UserCourseRoleTestRepository userCourseRoleTestRepository;

    @Mock
    private AuthorityService authorityService;

    @Mock
    private ArtemisAuthenticationProvider artemisAuthenticationProvider;

    @Mock
    private JWTCookieService jwtCookieService;

    private Exercise exercise;

    private LtiService ltiService;

    private OnlineCourseConfiguration onlineCourseConfiguration;

    private User user;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        ltiService = new LtiService(userCreationService, userRepository, userCourseRoleTestRepository, authorityService, artemisAuthenticationProvider, jwtCookieService);
        Course course = new Course();
        course.setId(100L);
        onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setCourse(course);
        exercise = new TextExercise();
        exercise.setCourse(course);
        user = new User();
        user.setLogin("login");
        user.setPassword("password");
        user.setLtiCreated(true);
        // A usable account by default; the tests that exercise the account-state guard set this to false explicitly.
        user.setActivated(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(userCreationService, userRepository, userCourseRoleTestRepository, authorityService, artemisAuthenticationProvider, jwtCookieService);
    }

    @Test
    void addLtiQueryParamsNewUser() {
        when(userRepository.getUser()).thenReturn(user);
        // Activated, as an LTI-provisioned account now always is: the dialog is offered on the initialisation marker.
        user.setActivated(true);
        user.setLtiCreated(true);
        user.setLtiInitialized(false);
        when(jwtCookieService.buildLoginCookie(true)).thenReturn(mock(ResponseCookie.class));

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.newInstance();
        HttpServletResponse response = mock(HttpServletResponse.class);

        ltiService.buildLtiResponse(uriComponentsBuilder, response);

        UriComponents uriComponents = uriComponentsBuilder.build();

        verify(jwtCookieService).buildLoginCookie(true);
        verify(response).addHeader(any(), any());

        String initialize = uriComponents.getQueryParams().getFirst("initialize");
        assertThat(initialize).isEmpty();
    }

    @Test
    void addLtiQueryParamsExistingUser() {
        when(userRepository.getUser()).thenReturn(user);
        user.setActivated(true);
        user.setLtiCreated(true);
        user.setLtiInitialized(true);
        when(jwtCookieService.buildLoginCookie(true)).thenReturn(mock(ResponseCookie.class));

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.newInstance();
        HttpServletResponse response = mock(HttpServletResponse.class);

        ltiService.buildLtiResponse(uriComponentsBuilder, response);

        UriComponents uriComponents = uriComponentsBuilder.build();

        verify(jwtCookieService).buildLoginCookie(true);
        verify(response).addHeader(any(), any());

        String initialize = uriComponents.getQueryParams().getFirst("initialize");
        assertThat(initialize).isNull();
    }

    @Test
    void addLtiQueryParamsDeactivatedNonLtiUserGetsNoDialog() {
        when(userRepository.getUser()).thenReturn(user);
        // A deactivated account that the launch did not create: the initialization dialog used to be offered purely
        // because it was not activated, and initializing then activated it.
        user.setActivated(false);
        user.setLtiCreated(false);
        when(jwtCookieService.buildLoginCookie(true)).thenReturn(mock(ResponseCookie.class));

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.newInstance();
        ltiService.buildLtiResponse(uriComponentsBuilder, mock(HttpServletResponse.class));

        assertThat(uriComponentsBuilder.build().getQueryParams().getFirst("initialize")).isNull();
    }

    @Test
    void successFullAuthentication() {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        when(userCourseRoleTestRepository.existsByUser_IdAndCourse_IdAndRole(any(), any(), any())).thenReturn(false);
        when(userRepository.findOneWithAuthoritiesByLogin(user.getLogin())).thenReturn(Optional.of(user));
        when(authorityService.buildAuthorities(user)).thenReturn(new HashSet<>());

        ltiService.onSuccessfulLtiAuthentication(user, exercise);

        ArgumentCaptor<UserCourseRole> ucrCaptor = ArgumentCaptor.forClass(UserCourseRole.class);
        verify(userCourseRoleTestRepository).save(ucrCaptor.capture());
        UserCourseRole savedUcr = ucrCaptor.getValue();
        assertThat(savedUcr.getUser()).isEqualTo(user);
        assertThat(savedUcr.getCourse()).isEqualTo(course);
        assertThat(savedUcr.getRole()).isEqualTo(CourseRole.STUDENT);
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
        when(userCreationService.createUser(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(user);

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
        when(userCreationService.createUser(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(user);

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
    void authenticateLtiUser_newUserIsCreatedActivated() {
        SecurityContextHolder.getContext().setAuthentication(null);

        User unactivatedUser = new User();
        unactivatedUser.setLogin("username");
        unactivatedUser.setPassword("password");
        // What the factory returns on an instance that has self-registration enabled.
        unactivatedUser.setActivated(false);
        unactivatedUser.setActivationKey("activation-key");

        when(artemisAuthenticationProvider.getUsernameForEmail("email")).thenReturn(Optional.empty());
        when(userRepository.findOneByEmailIgnoreCase("email")).thenReturn(Optional.empty());
        when(userRepository.findOneByLogin("username")).thenReturn(Optional.empty());
        when(userCreationService.createUser(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(unactivatedUser);

        ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", false);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        // An LTI user is provisioned by the launch and never receives an activation mail, so leaving them unactivated would produce an
        // account that can never authenticate outside the launch - it would be rejected by password login and by git authentication.
        assertThat(savedUser.getValue().getActivated()).as("LTI user is activated").isTrue();
        assertThat(savedUser.getValue().getActivationKey()).as("LTI user keeps no activation key").isNull();
        assertThat(savedUser.getValue().isLtiCreated()).isTrue();
    }

    /**
     * The launch writes the security context directly rather than going through an AuthenticationProvider, so it does not
     * inherit the account-state check the providers perform. Without an explicit one, a launch would be a way around an
     * administrator's deactivation.
     */
    @Test
    void authenticateLtiUser_deactivatedExistingUser_isRefused() {
        SecurityContextHolder.getContext().setAuthentication(null);
        user.setActivated(false);

        when(artemisAuthenticationProvider.getUsernameForEmail("email")).thenReturn(Optional.empty());
        when(userRepository.findOneByEmailIgnoreCase("email")).thenReturn(Optional.empty());
        when(userRepository.findOneByLogin("username")).thenReturn(Optional.of(user));

        assertThatExceptionOfType(InternalAuthenticationServiceException.class)
                .isThrownBy(() -> ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", false));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).as("no session is established").isNull();
    }

    @Test
    void authenticateLtiUser_softDeletedExistingUser_isRefused() {
        SecurityContextHolder.getContext().setAuthentication(null);
        user.setActivated(true);
        user.setDeleted(true);

        when(artemisAuthenticationProvider.getUsernameForEmail("email")).thenReturn(Optional.empty());
        when(userRepository.findOneByEmailIgnoreCase("email")).thenReturn(Optional.empty());
        when(userRepository.findOneByLogin("username")).thenReturn(Optional.of(user));

        assertThatExceptionOfType(InternalAuthenticationServiceException.class)
                .isThrownBy(() -> ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", false));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).as("no session is established").isNull();
    }

    @Test
    void authenticateLtiUser_deactivatedUserOnTrustedSystem_isRefused() {
        SecurityContextHolder.getContext().setAuthentication(null);
        ReflectionTestUtils.setField(ltiService, "trustExternalLTISystems", true);
        user.setActivated(false);

        when(artemisAuthenticationProvider.getUsernameForEmail("email")).thenReturn(Optional.of("username"));
        when(userRepository.findOneWithAuthoritiesByEmail("email")).thenReturn(Optional.of(user));

        assertThatExceptionOfType(InternalAuthenticationServiceException.class)
                .isThrownBy(() -> ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", false));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).as("no session is established").isNull();
    }

    @Test
    void authenticateLtiUser_activatedExistingUser_isSignedIn() {
        SecurityContextHolder.getContext().setAuthentication(null);
        user.setActivated(true);

        when(artemisAuthenticationProvider.getUsernameForEmail("email")).thenReturn(Optional.empty());
        when(userRepository.findOneByEmailIgnoreCase("email")).thenReturn(Optional.empty());
        when(userRepository.findOneByLogin("username")).thenReturn(Optional.of(user));

        ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", false);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user.getLogin());
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
        user.setLtiCreated(true);

        assertThat(ltiService.isLtiCreatedUser(user)).isTrue();
    }

    @Test
    void isNotLtiCreatedUser() {
        user.setLtiCreated(false);

        assertThat(ltiService.isLtiCreatedUser(user)).isFalse();
    }
}
