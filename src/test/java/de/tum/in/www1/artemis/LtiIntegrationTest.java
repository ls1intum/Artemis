package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

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

class LtiIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "ltiintegrationtest";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private ProgrammingExercise programmingExercise;

    private Course course;

    @Autowired
    private CourseRepository courseRepository;

    @Value("${artemis.user-management.external.user}")
    private String jiraUser;

    @Value("${artemis.user-management.external.password}")
    private String jiraPassword;

    private static final String EDX_REQUEST_BODY = """
            custom_component_display_name=Exercise\
            &lti_version=LTI-1p0\
            &oauth_nonce=171298047571430710991572204884\
            &resource_link_id=courses.edx.org-16a90aca094448ab95caf484b5c35d32\
            &context_id=course-v1%3ATUMx%2BSEECx%2B1T2018\
            &oauth_signature_method=HMAC-SHA1\
            &oauth_timestamp=1572204884\
            &lis_person_contact_email_primary=__EMAIL__\
            &oauth_signature=GYXApaIv0x7k%2FOPT9%2FoU38IBQRc%3D\
            &context_title=Software+Engineering+Essentials\
            &lti_message_type=basic-lti-launch-request\
            &launch_presentation_return_url=\
            &context_label=TUMx\
            &user_id=ff30145d6884eeb2c1cef50298939383\
            &roles=Student\
            &oauth_version=1.0\
            &oauth_consumer_key=artemis_lti_key\
            &lis_result_sourcedid=course-v1%253ATUMx%252BSEECx%252B1T2018%3Acourses.edx.org-16a90aca094448ab95caf484b5c35d32%3Aff30145d6884eeb2c1cef50298939383\
            &launch_presentation_locale=en\
            &lis_outcome_service_url=https%3A%2F%2Fcourses.edx.org%2Fcourses%2Fcourse-v1%3ATUMx%2BSEECx%2B1T2018%2Fxblock%2Fblock-v1%3ATUMx%2BSEECx%2B1T2018%2Btype%40lti_consumer%2Bblock%4016a90aca094448ab95caf484b5c35d32%2Fhandler_noauth%2Foutcome_service_handler\
            &lis_person_sourcedid=lovaiible\
            &oauth_callback=about%3Ablank""";

    private static final String MOODLE_REQUEST_BODY = """
            oauth_version=1.0\
            &oauth_timestamp=1659585343\
            &oauth_nonce=ce994a9669026380ec4d2c6e2722460a\
            &oauth_consumer_key=artemis_lti_key\
            &user_id=11\
            &lis_person_sourcedid=\
            &roles=Learner\
            &context_id=3\
            &context_label=MO1\
            &context_title=TestCourseNonTUMX\
            &resource_link_title=LTI\
            &resource_link_description=\
            &resource_link_id=5\
            &context_type=CourseSection\
            &lis_course_section_sourcedid=\
            &lis_result_sourcedid=%7B%22data%22%3A%7B%22instanceid%22%3A%225%22%2C%22userid%22%3A%2211%22%2C%22typeid%22%3A%225%22%2C%22launchid%22%3A1792115554%7D%2C%22hash%22%3A%22d7a145eb9d0afd5aeff342de0b8a10ddd8b2344bbdcf544b0af580cc3209d636%22%7D\
            &lis_outcome_service_url=http%3A%2F%2Flocalhost%3A81%2Fmod%2Flti%2Fservice.php\
            &lis_person_name_given=carlos\
            &lis_person_name_family=moodle\
            &lis_person_name_full=carlos+moodle\
            &ext_user_username=carlosmoodle\
            &lis_person_contact_email_primary=__EMAIL__\
            &launch_presentation_locale=en\
            &ext_lms=moodle-2\
            &tool_consumer_info_product_family_code=moodle\
            &tool_consumer_info_version=2021051707\
            &oauth_callback=about%3Ablank\
            &lti_version=LTI-1p0\
            &lti_message_type=basic-lti-launch-request\
            &tool_consumer_instance_guid=localhost\
            &tool_consumer_instance_name=New+Site\
            &tool_consumer_instance_description=New+Site\
            &launch_presentation_document_target=window\
            &launch_presentation_return_url=http%3A%2F%2Flocalhost%3A81%2Fmod%2Flti%2Freturn.php%3Fcourse%3D3%26launch_container%3D4%26instanceid%3D5%26sesskey%3DBG6zIkjI4p\
            &oauth_signature_method=HMAC-SHA1\
            &oauth_signature=nj33KzZAyM%2Fg%2B3R1TVfQwpt7mPk%3D""";

    @BeforeEach
    void init() {
        /* We mock the following method because we don't have the OAuth secret for edx */
        doReturn(null).when(lti10Service).verifyRequest(any(), any());

        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        var user = userRepository.findUserWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").get();
        user.setInternal(false);
        userRepository.save(user);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        course.setOnlineCourse(true);
        courseUtilService.addOnlineCourseConfigurationToCourse(course);

        programmingExercise = programmingExerciseRepository.findByIdElseThrow(course.getExercises().stream().findFirst().get().getId());

        jiraRequestMockProvider.enableMockingOfRequests();
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
    @ValueSource(strings = { EDX_REQUEST_BODY }) // To be readded when LtiUserId is removed, MOODLE_REQUEST_BODY })
    @WithAnonymousUser
    void launchAsAnonymousUser_WithoutExistingEmail(String requestBody) throws Exception {
        String email = generateEmail("launchAsAnonymousUser_WithoutExistingEmail");
        requestBody = replaceEmail(requestBody, email);
        addJiraMocks(email, null);

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
        Long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        URI header = request.post("/api/public/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        var uriComponents = UriComponentsBuilder.fromUri(header).build();
        assertParametersExistingStudent(UriComponentsBuilder.fromUri(header).build().getQueryParams());
        assertThat(uriComponents.getPathSegments()).containsSequence("courses", courseId.toString(), "exercises", exerciseId.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY }) // To be readded when LtiUserId is removed, MOODLE_REQUEST_BODY })
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

        var nowIn20Minutes = ZonedDateTime.now().plus(20, ChronoUnit.MINUTES);

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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void dynamicRegistrationFailsAsStudent() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("openid_configuration", "configurationUrl");

        request.postWithoutResponseBody("/api/lti13/dynamic-registration/" + course.getId(), HttpStatus.FORBIDDEN, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void dynamicRegistrationFailsWithoutOpenIdConfiguration() throws Exception {
        request.postWithoutResponseBody("/api/lti13/dynamic-registration/" + course.getId(), HttpStatus.BAD_REQUEST, new LinkedMultiValueMap<>());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void dynamicRegistrationFailsForNonOnlineCourse() throws Exception {
        course.setOnlineCourse(false);
        course.setOnlineCourseConfiguration(null);
        courseRepository.save(course);

        var params = new LinkedMultiValueMap<String, String>();
        params.add("openid_configuration", "configurationUrl");

        request.postWithoutResponseBody("/api/lti13/dynamic-registration/" + course.getId(), HttpStatus.BAD_REQUEST, params);
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
