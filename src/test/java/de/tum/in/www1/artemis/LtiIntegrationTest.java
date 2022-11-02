package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseLtiConfigurationDTO;

class LtiIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private ProgrammingExercise programmingExercise;

    private Course course;

    @Autowired
    private CourseRepository courseRepository;

    private static final String EDX_REQUEST_BODY = """
            custom_component_display_name=Exercise\
            &lti_version=LTI-1p0\
            &oauth_nonce=171298047571430710991572204884\
            &resource_link_id=courses.edx.org-16a90aca094448ab95caf484b5c35d32\
            &context_id=course-v1%3ATUMx%2BSEECx%2B1T2018\
            &oauth_signature_method=HMAC-SHA1\
            &oauth_timestamp=1572204884\
            &custom_require_existing_user=false\
            &custom_lookup_user_by_email=true\
            &lis_person_contact_email_primary=anh.montag%40tum.de\
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
            &lis_person_contact_email_primary=carlosmoodle%40email.com\
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
            &custom_lookup_user_by_email=true\
            &launch_presentation_document_target=window\
            &launch_presentation_return_url=http%3A%2F%2Flocalhost%3A81%2Fmod%2Flti%2Freturn.php%3Fcourse%3D3%26launch_container%3D4%26instanceid%3D5%26sesskey%3DBG6zIkjI4p\
            &oauth_signature_method=HMAC-SHA1\
            &oauth_signature=nj33KzZAyM%2Fg%2B3R1TVfQwpt7mPk%3D""";

    @BeforeEach
    void init() {
        /* We mock the following method because we don't have the OAuth secret for edx */
        doReturn(null).when(ltiService).verifyRequest(any(), any());

        database.addUsers(1, 1, 0, 1);

        course = database.addCourseWithOneProgrammingExercise();
        course.setOnlineCourse(true);
        database.addOnlineCourseConfigurationToCourse(course);

        programmingExercise = programmingExerciseRepository.findAll().get(0);

        jiraRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    private void addJiraMocks(String requestBody, boolean existingUser) throws Exception {
        String email = "";
        if (Objects.equals(requestBody, EDX_REQUEST_BODY)) {
            email = "anh.montag@tum.de";
        }
        if (Objects.equals(requestBody, MOODLE_REQUEST_BODY)) {
            email = "carlosmoodle@email.com";
        }

        if (existingUser) {
            jiraRequestMockProvider.mockGetUsernameForEmail(email, email, "existingUser");
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
        course.setOnlineCourseConfiguration(null);
        courseRepository.save(course);

        request.postWithoutLocation("/api/lti/launch/" + programmingExercise.getId(), requestBody.getBytes(), HttpStatus.BAD_REQUEST, new HttpHeaders(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithAnonymousUser
    void launchAsAnonymousUser_WithoutExistingEmail(String requestBody) throws Exception {
        addJiraMocks(requestBody, false);

        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        URI header = request.post("/api/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        var uriComponents = UriComponentsBuilder.fromUri(header).build();
        assertParametersNewStudent(UriComponentsBuilder.fromUri(header).build().getQueryParams());
        assertThat(uriComponents.getPathSegments()).containsSequence("courses", courseId.toString(), "exercises", exerciseId.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithAnonymousUser
    void launchAsAnonymousUser_WithExistingEmail(String requestBody) throws Exception {
        addJiraMocks(requestBody, false);

        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        URI header = request.post("/api/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        var uriComponents = UriComponentsBuilder.fromUri(header).build();
        assertParametersNewStudent(UriComponentsBuilder.fromUri(header).build().getQueryParams());
        assertThat(uriComponents.getPathSegments()).containsSequence("courses", courseId.toString(), "exercises", exerciseId.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithAnonymousUser
    void launchAsAnonymousUser_checkExceptions(String requestBody) throws Exception {
        request.postWithoutLocation("/api/lti/launch/" + programmingExercise.getId() + 1, requestBody.getBytes(), HttpStatus.NOT_FOUND, new HttpHeaders(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);

        doThrow(ArtemisAuthenticationException.class).when(ltiService).handleLaunchRequest(any(), any(), any());
        request.postWithoutLocation("/api/lti/launch/" + programmingExercise.getId(), requestBody.getBytes(), HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);

        doReturn("error").when(ltiService).verifyRequest(any(), any());
        request.postWithoutLocation("/api/lti/launch/" + programmingExercise.getId(), requestBody.getBytes(), HttpStatus.UNAUTHORIZED, new HttpHeaders(),
                MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithMockUser(username = "student1", roles = "USER")
    void launchAsRecentlyCreatedStudent(String requestBody) throws Exception {
        addJiraMocks(requestBody, true);

        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        URI header = request.post("/api/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        var uriComponents = UriComponentsBuilder.fromUri(header).build();
        assertParametersExistingStudent(UriComponentsBuilder.fromUri(header).build().getQueryParams());
        assertThat(uriComponents.getPathSegments()).containsSequence("courses", courseId.toString(), "exercises", exerciseId.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { EDX_REQUEST_BODY, MOODLE_REQUEST_BODY })
    @WithMockUser(username = "student1", roles = "USER")
    void launchAsExistingStudent(String requestBody) throws Exception {
        addJiraMocks(requestBody, true);

        var nowIn20Minutes = ZonedDateTime.now().plus(20, ChronoUnit.MINUTES);

        // Mock that student1 already exists since 20 min
        doReturn(nowIn20Minutes).when(timeService).now();

        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        URI header = request.post("/api/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        var uriComponents = UriComponentsBuilder.fromUri(header).build();
        assertParametersExistingStudent(UriComponentsBuilder.fromUri(header).build().getQueryParams());
        assertThat(uriComponents.getPathSegments()).containsSequence("courses", courseId.toString(), "exercises", exerciseId.toString());

        Mockito.reset(timeService);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void exerciseLtiConfiguration() throws Exception {
        request.get("/api/lti/configuration/" + programmingExercise.getId(), HttpStatus.OK, ExerciseLtiConfigurationDTO.class);
        request.get("/api/lti/configuration/1234254354", HttpStatus.NOT_FOUND, ExerciseLtiConfigurationDTO.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void exerciseLtiConfiguration_withEmptyConfig() throws Throwable {
        course.setOnlineCourseConfiguration(null);
        courseRepository.save(course);

        request.get("/api/lti/configuration/" + programmingExercise.getId(), HttpStatus.BAD_REQUEST, ExerciseLtiConfigurationDTO.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void exerciseLtiConfigurationAsStudent() throws Exception {
        course.setInstructorGroupName("123");
        courseRepository.save(course);
        request.get("/api/lti/configuration/" + programmingExercise.getId(), HttpStatus.FORBIDDEN, ExerciseLtiConfigurationDTO.class);
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
