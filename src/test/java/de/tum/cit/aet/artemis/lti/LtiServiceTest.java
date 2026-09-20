package de.tum.cit.aet.artemis.lti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.security.ArtemisAuthenticationProvider;
import de.tum.cit.aet.artemis.account.service.UserRecoveryKeyService;
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
import de.tum.cit.aet.artemis.lti.domain.UserLti;
import de.tum.cit.aet.artemis.lti.repository.UserLtiRepository;
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

    @Mock
    private UserLtiRepository userLtiRepository;

    @Mock
    private UserRecoveryKeyService userRecoveryKeyService;

    private Exercise exercise;

    private LtiService ltiService;

    private OnlineCourseConfiguration onlineCourseConfiguration;

    private User user;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        ltiService = new LtiService(userCreationService, userRepository, userCourseRoleTestRepository, authorityService, artemisAuthenticationProvider, jwtCookieService,
                userLtiRepository, userRecoveryKeyService);
        Course course = new Course();
        course.setId(100L);
        onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setCourse(course);
        exercise = new TextExercise();
        exercise.setCourse(course);
        user = new User();
        // The launch marker is a row keyed on the user id, so the fixture needs one.
        user.setId(1L);
        user.setLogin("login");
        user.setPassword("password");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(userCreationService, userRepository, userCourseRoleTestRepository, authorityService, artemisAuthenticationProvider, jwtCookieService, userLtiRepository,
                userRecoveryKeyService);
    }

    @Test
    void addLtiQueryParamsNewUser() {
        when(userRepository.getUser()).thenReturn(user);
        // The dialog is offered on the launch's own marker, not on `activated`: a deactivated account is inactive too, and
        // offering it the dialog only produces a request the endpoint refuses.
        when(userLtiRepository.existsByUserIdAndCreatedByLaunchIsTrueAndInitializedIsFalse(user.getId())).thenReturn(true);
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
        // Nothing outstanding, so no dialog.
        when(userLtiRepository.existsByUserIdAndCreatedByLaunchIsTrueAndInitializedIsFalse(user.getId())).thenReturn(false);
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
    void authenticateLtiUser_noAuth() {
        SecurityContextHolder.getContext().setAuthentication(null);
        onlineCourseConfiguration.setRequireExistingUser(true);

        assertThatExceptionOfType(InternalAuthenticationServiceException.class)
                .isThrownBy(() -> ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", onlineCourseConfiguration.isRequireExistingUser()));
    }

    /**
     * A launch that provisions a new account has to leave two things behind: the marker row in {@code user_lti}, and no
     * activation key. {@code createUser} issues one for every internal account, but a launch-provisioned account never
     * receives the activation mail, so a key left in place would be a link nobody sent that still flips {@code activated}
     * back on. See {@link de.tum.cit.aet.artemis.account.domain.User#activated}.
     */
    @Test
    void authenticateLtiUser_createsTheLaunchMarkerAndDropsTheActivationKey() {
        SecurityContextHolder.getContext().setAuthentication(null);
        when(artemisAuthenticationProvider.getUsernameForEmail("email")).thenReturn(Optional.empty());
        when(userRepository.findOneByEmailIgnoreCase("email")).thenReturn(Optional.empty());
        when(userRepository.findOneByLogin("username")).thenReturn(Optional.empty());
        User created = new User();
        created.setId(42L);
        created.setLogin("username");
        created.setPassword("password");
        when(userCreationService.createUser(eq("username"), anyString(), eq("firstname"), eq("lastname"), eq("email"), isNull(), isNull(), anyString(), eq(true)))
                .thenReturn(created);

        ltiService.authenticateLtiUser("email", "username", "firstname", "lastname", false);

        var marker = ArgumentCaptor.forClass(UserLti.class);
        verify(userLtiRepository).save(marker.capture());
        assertThat(marker.getValue().getUserId()).isEqualTo(42L);
        assertThat(marker.getValue().isCreatedByLaunch()).isTrue();
        verify(userRecoveryKeyService).clearActivationKey(42L);
        // createUser already persisted the account; saving it again here would be a redundant write.
        verify(userRepository, never()).save(created);
    }

    @Test
    void isLtiCreatedUser() {
        when(userLtiRepository.existsByUserIdAndCreatedByLaunchIsTrue(user.getId())).thenReturn(true);

        assertThat(ltiService.isLtiCreatedUser(user)).isTrue();
    }

    @Test
    void authenticateLtiUser_caseInsensitiveEmailLookup() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(null);

        // Set trustExternalLTISystems to true via reflection
        var field = LtiService.class.getDeclaredField("trustExternalLTISystems");
        field.setAccessible(true);
        field.set(ltiService, true);

        String emailInDb = "John.Doe@test.com";
        String emailFromLti = "john.doe@test.com";
        user.setEmail(emailInDb);

        when(userRepository.findOneByEmailIgnoreCase(emailFromLti)).thenReturn(Optional.of(user));
        when(userRepository.findOneWithAuthoritiesByEmailIgnoreCase(emailFromLti)).thenReturn(Optional.of(user));

        ltiService.authenticateLtiUser(emailFromLti, "username", "firstname", "lastname", false);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo(user.getLogin());
    }

    @Test
    void isNotLtiCreatedUser() {
        // No row at all, which is how an account that no launch created is represented.
        when(userLtiRepository.existsByUserIdAndCreatedByLaunchIsTrue(user.getId())).thenReturn(false);

        assertThat(ltiService.isLtiCreatedUser(user)).isFalse();
    }
}
