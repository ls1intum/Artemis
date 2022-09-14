package de.tum.in.www1.artemis.service.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.in.www1.artemis.authentication.AuthenticationIntegrationTestHelper;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;

class LtiServiceTest {

    @Mock
    private UserCreationService userCreationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    @Mock
    private ArtemisAuthenticationProvider artemisAuthenticationProvider;

    @Mock
    private LtiUserIdRepository ltiUserIdRepository;

    private Exercise exercise;

    private LtiService ltiService;

    private LtiLaunchRequestDTO launchRequest;

    private OnlineCourseConfiguration onlineCourseConfiguration;

    private User user;

    private LtiUserId ltiUserId;

    private final String courseStudentGroupName = "courseStudentGroupName";

    private LtiOutcomeUrl ltiOutcomeUrl;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        ltiService = new LtiService(userCreationService, userRepository, ltiOutcomeUrlRepository, resultRepository, onlineCourseConfigurationRepository,
                artemisAuthenticationProvider, ltiUserIdRepository);
        Course course = new Course();
        course.setStudentGroupName(courseStudentGroupName);
        onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setCourse(course);
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
    void handleLaunchRequest_LTILaunchFromEdxStudio() {
        launchRequest.setUser_id("student");
        var exception = assertThrows(InternalAuthenticationServiceException.class, () -> ltiService.handleLaunchRequest(launchRequest, exercise, onlineCourseConfiguration));
        String expectedMessage = "Invalid username sent by launch request. Please do not launch the exercise from edX studio. Use 'Preview' instead.";
        assertThat(exception.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void handleLaunchRequest_NoContactEmail() {
        String expectedMessage = "No email address sent by launch request. Please make sure the user has an accessible email address.";
        launchRequest.setLis_person_contact_email_primary(null);
        var exceptionNull = assertThrows(InternalAuthenticationServiceException.class, () -> ltiService.handleLaunchRequest(launchRequest, exercise, onlineCourseConfiguration));
        launchRequest.setLis_person_contact_email_primary("");
        var exceptionEmpty = assertThrows(InternalAuthenticationServiceException.class, () -> ltiService.handleLaunchRequest(launchRequest, exercise, onlineCourseConfiguration));

        assertThat(exceptionNull.getMessage()).isEqualTo(expectedMessage);
        assertThat(exceptionEmpty.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void handleLaunchRequest_existingMappingForLtiUserId() {
        when(ltiUserIdRepository.findByLtiUserId(launchRequest.getUser_id())).thenReturn(Optional.of(ltiUserId));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);
        onSuccessfulAuthenticationSetup(user, ltiUserId);
        ltiService.handleLaunchRequest(launchRequest, exercise, onlineCourseConfiguration);
        onSuccessfulAuthenticationAssertions(user, ltiUserId);
    }

    @Test
    void handleLaunchRequest_lookupWithLtiEmailAddress() {
        String username = "username";
        String email = launchRequest.getLis_person_contact_email_primary();
        launchRequest.setCustom_lookup_user_by_email(true);
        when(ltiUserIdRepository.findByLtiUserId(launchRequest.getUser_id())).thenReturn(Optional.empty());
        when(artemisAuthenticationProvider.getUsernameForEmail(email)).thenReturn(Optional.of(username));
        when(artemisAuthenticationProvider.getOrCreateUser(new UsernamePasswordAuthenticationToken(username, ""), null, null, email, true)).thenReturn(user);

        onSuccessfulAuthenticationSetup(user, ltiUserId);
        ltiService.handleLaunchRequest(launchRequest, exercise, onlineCourseConfiguration);
        onSuccessfulAuthenticationAssertions(user, ltiUserId);
    }

    @Test
    void handleLaunchRequest_newUserIsNotRequired() {
        String username = "moodle_username";
        String firstName = "firstName";
        String lastName = "lastName";

        launchRequest.setExt_user_username("username");
        launchRequest.setLis_person_name_given(firstName);
        launchRequest.setLis_person_name_family(lastName);

        Set<String> groups = new HashSet<>();
        groups.add(LtiService.LTI_GROUP_NAME);
        user.setActivated(false);
        when(ltiUserIdRepository.findByLtiUserId(launchRequest.getUser_id())).thenReturn(Optional.empty());
        when(userRepository.findOneByLogin(username)).thenReturn(Optional.empty());
        when(userCreationService.createUser(username, null, groups, firstName, lastName, launchRequest.getLis_person_contact_email_primary(), null, null, "en", true))
                .thenReturn(user);

        onSuccessfulAuthenticationSetup(user, ltiUserId);
        ltiService.handleLaunchRequest(launchRequest, exercise, onlineCourseConfiguration);
        onSuccessfulAuthenticationAssertions(user, ltiUserId);
    }

    @Test
    void handleLaunchRequest_noAuthentication() {
        launchRequest.setCustom_require_existing_user(true);
        when(ltiUserIdRepository.findByLtiUserId(launchRequest.getUser_id())).thenReturn(Optional.empty());

        var exception = assertThrows(InternalAuthenticationServiceException.class, () -> ltiService.handleLaunchRequest(launchRequest, exercise, onlineCourseConfiguration));
        String expectedMessage = "Could not find existing user or create new LTI user.";
        assertThat(exception.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void handleLaunchRequest_alreadyAuthenticated() {
        launchRequest.setCustom_require_existing_user(true);
        when(ltiUserIdRepository.findByLtiUserId(launchRequest.getUser_id())).thenReturn(Optional.empty());
        SecurityUtils.setAuthorizationObject();
        onSuccessfulAuthenticationSetup(user, ltiUserId);
        ltiService.handleLaunchRequest(launchRequest, exercise, onlineCourseConfiguration);
        onSuccessfulAuthenticationAssertions(user, ltiUserId);
    }

    @Test
    void onSuccessfulLtiAuthentication() {
        ltiUserId.setLtiUserId("oldStudentId");
        onSuccessfulAuthenticationSetup(user, ltiUserId);
        ltiService.onSuccessfulLtiAuthentication(launchRequest, exercise);
        onSuccessfulAuthenticationAssertions(user, ltiUserId);
    }

    @Test
    void onSuccessfulLtiAuthenticationWithoutUrl() {
        launchRequest.setLis_outcome_service_url(null);
        launchRequest.setUser_id(null);
        onSuccessfulAuthenticationSetup(user, ltiUserId);
        ltiService.onSuccessfulLtiAuthentication(launchRequest, exercise);
        assertNull(ltiOutcomeUrl.getUrl());
        assertNull(ltiUserId.getLtiUserId());
    }

    private void onSuccessfulAuthenticationSetup(User user, LtiUserId ltiUserId) {
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);
        when(ltiUserIdRepository.findByUser(user)).thenReturn(Optional.of(ltiUserId));
        ltiOutcomeUrlRepositorySetup(user);
    }

    private void ltiOutcomeUrlRepositorySetup(User user) {
        when(ltiOutcomeUrlRepository.findByUserAndExercise(user, exercise)).thenReturn(Optional.of(ltiOutcomeUrl));
    }

    private void onSuccessfulAuthenticationAssertions(User user, LtiUserId ltiUserId) {
        assertThat(user.getGroups()).contains(courseStudentGroupName);
        assertThat(user.getGroups()).contains(LtiService.LTI_GROUP_NAME);
        assertThat("ff30145d6884eeb2c1cef50298939383").isEqualTo(ltiUserId.getLtiUserId());
        assertThat("some.outcome.service.url.com").isEqualTo(ltiOutcomeUrl.getUrl());
        assertThat("someResultSourceId").isEqualTo(ltiOutcomeUrl.getSourcedId());
        verify(userCreationService, times(1)).saveUser(user);
        verify(artemisAuthenticationProvider, times(1)).addUserToGroup(user, courseStudentGroupName);
        verify(ltiOutcomeUrlRepository, times(1)).save(ltiOutcomeUrl);
    }

    @Test
    void verifyRequest_oauthSecretNotSpecified() {
        onlineCourseConfiguration.setLtiSecret(null);

        HttpServletRequest request = mock(HttpServletRequest.class);
        String message = ltiService.verifyRequest(request, onlineCourseConfiguration);
        assertThat("verifyRequest for LTI is not supported on this Artemis instance, artemis.lti.oauth-secret was not specified in the yml configuration").isEqualTo(message);
    }

    @Test
    void verifyRequest_unsuccessfulVerification() {
        onlineCourseConfiguration.setLtiSecret("secret");

        String url = "https://some.url.com";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI(url);
        String message = ltiService.verifyRequest(request, onlineCourseConfiguration);
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
        when(onlineCourseConfigurationRepository.findByCourseIdOrElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId())).thenReturn(onlineCourseConfiguration);

        ltiService.onNewResult(participation);
        verify(resultRepository, times(1)).findFirstByParticipationIdOrderByCompletionDateDesc(27L);
    }
}
