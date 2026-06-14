package de.tum.cit.aet.artemis.lti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        user.setLtiCreated(true);   // replaces legacy LTI_GROUP_NAME group
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
        user.setActivated(false);
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
        String courseStudentGroupName = "course100-students";
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        course.setStudentGroupName(courseStudentGroupName);
        when(userCourseRoleTestRepository.existsByUser_IdAndCourse_IdAndRole(any(), any(), any())).thenReturn(false);
        when(authorityService.buildAuthorities(user)).thenReturn(new HashSet<>());

        ltiService.onSuccessfulLtiAuthentication(user, exercise);

        ArgumentCaptor<UserCourseRole> ucrCaptor = ArgumentCaptor.forClass(UserCourseRole.class);
        verify(userCourseRoleTestRepository).save(ucrCaptor.capture());
        UserCourseRole savedUcr = ucrCaptor.getValue();
        assertThat(savedUcr.getUser()).isEqualTo(user);
        assertThat(savedUcr.getCourse()).isEqualTo(course);
        assertThat(savedUcr.getRole()).isEqualTo(CourseRole.STUDENT);
        assertThat(user.getGroups()).contains(courseStudentGroupName);
        verify(userCreationService).saveUser(user);
    }

    /**
     * Regression test: Lti13Service loads users via {@code findOneWithCourseRolesAndAuthoritiesByLogin},
     * which does NOT include the groups entity graph. The old code called {@code user.getGroups().contains()}
     * directly, causing {@code LazyInitializationException} (session closed) or NPE in
     * {@code PersistentSet.hasDeletes()} at flush time. The fix re-fetches the user with groups initialized
     * when Hibernate reports the collection as uninitialized.
     * <p>
     * TODO (follow-up PR for #12788): remove this test once the user_groups table is dropped and
     * enrollUserInCourse no longer needs the groups entity graph at all.
     */
    @Test
    void enrollUserInCourse_groupsNotInitialized_reloadsUserBeforeDualWrite() {
        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            // simulate: user.groups is a lazy PersistentSet that has not been initialized
            hibernateMock.when(() -> Hibernate.isInitialized(user.getGroups())).thenReturn(false);

            User reloadedUser = new User();
            reloadedUser.setLogin("login");
            reloadedUser.setGroups(new HashSet<>());

            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            when(userRepository.findOneWithGroupsAndCourseRolesAndAuthoritiesByLogin(user.getLogin())).thenReturn(Optional.of(reloadedUser));
            when(userCourseRoleTestRepository.existsByUser_IdAndCourse_IdAndRole(any(), any(), any())).thenReturn(true);
            when(authorityService.buildAuthorities(reloadedUser)).thenReturn(new HashSet<>());

            ltiService.onSuccessfulLtiAuthentication(user, exercise);

            // must re-fetch the user with groups before writing to user_groups
            verify(userRepository).findOneWithGroupsAndCourseRolesAndAuthoritiesByLogin(user.getLogin());
            // must save the reloaded user (not the original one with uninitialized groups)
            verify(userCreationService).saveUser(reloadedUser);
        }
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
        user.setLtiCreated(true);

        assertThat(ltiService.isLtiCreatedUser(user)).isTrue();
    }

    @Test
    void isNotLtiCreatedUser() {
        user.setLtiCreated(false);

        assertThat(ltiService.isLtiCreatedUser(user)).isFalse();
    }
}
