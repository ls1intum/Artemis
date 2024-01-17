package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.util.LtiRequestBodies.EDX_REQUEST_BODY;
import static de.tum.in.www1.artemis.util.LtiRequestBodies.MOODLE_REQUEST_BODY;
import static de.tum.in.www1.artemis.util.TestUriParamsUtil.assertUriParamsContain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.open.PublicLtiResource;
import io.jsonwebtoken.Jwts;

/**
 * LTI 1.3 Exercise Launch
 * Note that Step 1. of the OpenID Connect Third Party intiated login flow is handled entirely by spring-security-lti13
 * which does not require additional testing here.
 * Testing all possible cases of Step 3. of the OpenID Connect Third Party initiated login flow is
 * nearly impossible if spring-security-lti13 is not mocked. Because of that, there is not a full integration test
 * provided here.
 * However, Lti13LaunchFilter is responsible to handle this step and is tested extensively.
 * see <a href="https://www.imsglobal.org/spec/lti/v1p3/#lti-message-general-details">LTI message general details</a>
 * see <a href="https://www.imsglobal.org/spec/security/v1p0/#openid_connect_launch_flow">OpenId Connect launch flow</a>
 */
class Lti13LaunchIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final SecretKey SIGNING_KEY = Jwts.SIG.HS256.key().build();

    private static final String VALID_ID_TOKEN = Jwts.builder().expiration(Date.from(Instant.now().plusSeconds(60))).issuer("https://example.com").audience().add("client-id").and()
            .id("1234").signWith(SIGNING_KEY).compact();

    private static final String OUTDATED_TOKEN = Jwts.builder().expiration(Date.from(Instant.now().minusSeconds(60))).issuer("https://example.com").audience().add("client-id")
            .and().id("1234").signWith(SIGNING_KEY).compact();

    private static final String VALID_STATE = "validState";

    private static final String TEST_PREFIX = "lti13launchintegrationtest";

    private Course course;

    private ProgrammingExercise programmingExercise;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private CourseRepository courseRepository;

    @Value("${artemis.user-management.external.user}")
    private String jiraUser;

    @Value("${artemis.user-management.external.password}")
    private String jiraPassword;

    @BeforeEach
    void init() {
        /* We mock the following method because we don't have the OAuth secret for edx */
        doReturn(null).when(lti10Service).verifyRequest(any(), any());

        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        var user = userRepository.findUserWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        user.setInternal(false);
        userRepository.save(user);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        course.setOnlineCourse(true);
        courseUtilService.addOnlineCourseConfigurationToCourse(course);

        programmingExercise = programmingExerciseRepository.findByIdElseThrow(course.getExercises().stream().findFirst().orElseThrow().getId());

        jiraRequestMockProvider.enableMockingOfRequests();
    }

    @Test
    @WithAnonymousUser
    void redirectProxy() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", VALID_ID_TOKEN);
        body.put("state", VALID_STATE);

        URI header = request.postForm(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.FOUND);

        validateRedirect(header, VALID_ID_TOKEN);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyNoState() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", VALID_ID_TOKEN);

        request.postFormWithoutLocation(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyNoToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);

        request.postFormWithoutLocation(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyInvalidToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);
        body.put("id_token", "invalid-token");

        request.postFormWithoutLocation(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyOutdatedToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);
        body.put("id_token", OUTDATED_TOKEN);

        request.postFormWithoutLocation(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyTokenInvalidSignature() throws Exception {
        // We can't validate the signature, hence we ignore it.
        String invalidSignatureToken = VALID_ID_TOKEN.substring(0, VALID_ID_TOKEN.lastIndexOf(".")) + OUTDATED_TOKEN.substring(OUTDATED_TOKEN.lastIndexOf("."));
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);
        body.put("id_token", invalidSignatureToken);

        URI header = request.postForm(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.FOUND);
        validateRedirect(header, invalidSignatureToken);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void oidcFlowFails_noRequestCached() throws Exception {
        String ltiLaunchUri = CustomLti13Configurer.LTI13_LOGIN_PATH + "?id_token=some-token&state=some-state";
        request.get(ltiLaunchUri, HttpStatus.INTERNAL_SERVER_ERROR, Object.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithAnonymousUser
    void launchAsAnonymousUser_noOnlineCourseConfigurationException(String requestBody) throws Exception {
        requestBody = replaceEmail(requestBody, generateEmail("launchAsAnonymousUser_noOnlineCourseConfigurationException"));

        course.setOnlineCourseConfiguration(null);
        courseRepository.save(course);

        request.postWithoutLocation("/api/public/lti/launch/" + programmingExercise.getId(), requestBody.getBytes(), HttpStatus.BAD_REQUEST, new HttpHeaders(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithAnonymousUser
    void launchAsAnonymousUser_WithoutExistingEmail(String requestBody) throws Exception {
        String email = generateEmail("launchAsAnonymousUser_WithoutExistingEmail");
        requestBody = replaceEmail(requestBody, email);
        addJiraMocks(email, null);
        addBitbucketMock(requestBody);

        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        URI header = request.post("/api/public/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        var uriComponents = UriComponentsBuilder.fromUri(header).build();
        assertParametersNewStudent(UriComponentsBuilder.fromUri(header).build().getQueryParams());
        assertThat(uriComponents.getPathSegments()).containsSequence("courses", courseId.toString(), "exercises", exerciseId.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithAnonymousUser
    void launchAsAnonymousUser_WithExistingEmail(String requestBody) throws Exception {
        String email = generateEmail("launchAsAnonymousUser_WithExistingEmail");
        requestBody = replaceEmail(requestBody, email);
        addJiraMocks(email, TEST_PREFIX + "student1");

        Long exerciseId = programmingExercise.getId();
        request.postWithoutLocation("/api/public/lti/launch/" + exerciseId, requestBody.getBytes(), HttpStatus.BAD_REQUEST, new HttpHeaders(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithAnonymousUser
    void launchAsAnonymousUser_RequireExistingUser(String requestBody) throws Exception {
        String email = generateEmail("launchAsAnonymousUser_RequireExistingUser");
        requestBody = replaceEmail(requestBody, email);

        course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(course.getId());
        OnlineCourseConfiguration onlineCourseConfiguration = course.getOnlineCourseConfiguration();
        onlineCourseConfiguration.setRequireExistingUser(true);
        courseRepository.save(course);

        jiraRequestMockProvider.mockGetUsernameForEmailEmptyResponse(email);

        request.postWithoutLocation("/api/public/lti/launch/" + programmingExercise.getId(), requestBody.getBytes(), HttpStatus.BAD_REQUEST, new HttpHeaders(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithAnonymousUser
    void launchAsAnonymousUser_checkExceptions(String requestBody) throws Exception {
        requestBody = replaceEmail(requestBody, generateEmail("launchAsAnonymousUser_checkExceptions"));

        request.postWithoutLocation("/api/public/lti/launch/" + programmingExercise.getId() + 1, requestBody.getBytes(), HttpStatus.NOT_FOUND, new HttpHeaders(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);

        doThrow(ArtemisAuthenticationException.class).when(lti10Service).performLaunch(any(), any(), any());
        request.postWithoutLocation("/api/public/lti/launch/" + programmingExercise.getId(), requestBody.getBytes(), HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);

        doThrow(InternalAuthenticationServiceException.class).when(lti10Service).performLaunch(any(), any(), any());
        request.postWithoutLocation("/api/public/lti/launch/" + programmingExercise.getId(), requestBody.getBytes(), HttpStatus.BAD_REQUEST, new HttpHeaders(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);

        doReturn("error").when(lti10Service).verifyRequest(any(), any());
        request.postWithoutLocation("/api/public/lti/launch/" + programmingExercise.getId(), requestBody.getBytes(), HttpStatus.UNAUTHORIZED, new HttpHeaders(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void launchAsRecentlyCreatedStudent(String requestBody) throws Exception {
        User user = userRepository.getUser();
        userRepository.save(user);

        requestBody = replaceEmail(requestBody, user.getEmail());

        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        URI header = request.post("/api/public/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        var uriComponents = UriComponentsBuilder.fromUri(header).build();
        assertParametersExistingStudent(UriComponentsBuilder.fromUri(header).build().getQueryParams());
        assertThat(uriComponents.getPathSegments()).containsSequence("courses", courseId.toString(), "exercises", exerciseId.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void launchAsExistingStudent(String requestBody) throws Exception {
        User user = userRepository.getUser();
        userRepository.save(user);

        requestBody = replaceEmail(requestBody, user.getEmail());

        var nowIn20Minutes = ZonedDateTime.now().plusMinutes(20);

        // Mock that student1 already exists since 20 min
        doReturn(nowIn20Minutes).when(timeService).now();

        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        URI header = request.post("/api/public/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        var uriComponents = UriComponentsBuilder.fromUri(header).build();
        assertParametersExistingStudent(UriComponentsBuilder.fromUri(header).build().getQueryParams());
        assertThat(uriComponents.getPathSegments()).containsSequence("courses", courseId.toString(), "exercises", exerciseId.toString());

        Mockito.reset(timeService);
    }

    private void validateRedirect(URI locationHeader, String token) {
        assertThat(locationHeader.getPath()).isEqualTo(PublicLtiResource.LOGIN_REDIRECT_CLIENT_PATH);

        List<NameValuePair> params = URLEncodedUtils.parse(locationHeader, StandardCharsets.UTF_8);
        assertUriParamsContain(params, "id_token", token);
        assertUriParamsContain(params, "state", VALID_STATE);
    }

    private String generateEmail(String username) {
        return username + "@email.com";
    }

    private String replaceEmail(String requestBody, String email) {
        return requestBody.replace("__EMAIL__", email);
    }

    private void addJiraMocks(String email, String existingUser) throws Exception {

        if (existingUser != null) {
            jiraRequestMockProvider.mockGetUsernameForEmail(email, email, existingUser);
            jiraRequestMockProvider.mockGetOrCreateUserLti(jiraUser, jiraPassword, existingUser, email, "", Set.of("students"));
        }
        else {
            jiraRequestMockProvider.mockGetUsernameForEmailEmptyResponse(email);
        }
        jiraRequestMockProvider.mockAddUserToGroup("tumuser", false);
    }

    private void addBitbucketMock(String requestBody) throws URISyntaxException {
        bitbucketRequestMockProvider.enableMockingOfRequests();

        String username = "prefix_";
        Matcher matcher = Pattern.compile("lis_person_sourcedid=([^&#]+)").matcher(requestBody);
        if (matcher.find() && !matcher.group(1).isEmpty()) {
            username += matcher.group(1);
        }
        else {
            matcher = Pattern.compile("ext_user_username=([^&#]+)").matcher(requestBody);
            if (matcher.find()) {
                username += matcher.group(1);
            }
        }
        bitbucketRequestMockProvider.mockUserExists(username);
    }

    private void assertParametersExistingStudent(MultiValueMap<String, String> parameters) {
        assertThat(parameters.getFirst("initialize")).isNull();
        assertThat(parameters.getFirst("ltiSuccessLoginRequired")).isNotNull();
    }

    private void assertParametersNewStudent(MultiValueMap<String, String> parameters) {
        assertThat(parameters.getFirst("initialize")).isNotNull();
        assertThat(parameters.getFirst("ltiSuccessLoginRequired")).isNull();
    }
}
