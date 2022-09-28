package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import de.tum.in.www1.artemis.util.ConfigUtil;
import de.tum.in.www1.artemis.web.rest.LtiResource;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseLtiConfigurationDTO;

class LtiIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private ProgrammingExercise programmingExercise;

    private Course course;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LtiResource ltiResource;

    private final String requestBody = """
            custom_component_display_name=Exercise\
            &lti_version=LTI-1p0\
            &oauth_nonce=171298047571430710991572204884\
            &resource_link_id=courses.edx.org-16a90aca094448ab95caf484b5c35d32\
            &context_id=course-v1%3ATUMx%2BSEECx%2B1T2018\
            &oauth_signature_method=HMAC-SHA1\
            &oauth_timestamp=1572204884\
            &custom_require_existing_user=false\
            &lis_person_contact_email_primary=anh.montag%40tum.de\
            &oauth_signature=GYXApaIv0x7k%2FOPT9%2FoU38IBQRc%3D\
            &context_title=Software+Engineering+Essentials\
            &lti_message_type=basic-lti-launch-request\
            &custom_lookup_user_by_email=false\
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

    @BeforeEach
    void init() {
        /* We mock the following method because we don't have the OAuth secret for edx */
        doReturn(null).when(ltiService).verifyRequest(any());

        database.addUsers(1, 1, 0, 1);

        course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(course.getExercises().stream().findFirst().get().getId());
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithAnonymousUser
    void launchAsAnonymousUser() throws Exception {
        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        URI header = request.post("/api/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        var uriComponents = UriComponentsBuilder.fromUri(header).build();
        MultiValueMap<String, String> parameters = UriComponentsBuilder.fromUri(header).build().getQueryParams();
        assertThat(parameters.getFirst("jwt")).isNotBlank();
        assertThat(parameters.getFirst("login")).isNull();
        assertThat(parameters.getFirst("initialize")).isNotNull();
        assertThat(uriComponents.getPathSegments()).containsSequence("courses", courseId.toString(), "exercises", exerciseId.toString());

        this.checkExceptions();
    }

    @Test
    @WithAnonymousUser
    void launchAsAnonymousUser_withEmptyConfig() throws Throwable {
        ConfigUtil.testWithChangedConfig(ltiResource, "LTI_ID", Optional.empty(), () -> {
            Long exerciseId = programmingExercise.getId();
            request.postWithoutLocation("/api/lti/launch/" + exerciseId, requestBody, HttpStatus.FORBIDDEN, null);
        });
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void launchAsRecentlyCreatedStudent() throws Exception {
        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        URI header = request.post("/api/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        var uriComponents = UriComponentsBuilder.fromUri(header).build();
        MultiValueMap<String, String> parameters = UriComponentsBuilder.fromUri(header).build().getQueryParams();
        assertThat(parameters.getFirst("jwt")).isNotBlank();
        assertThat(parameters.getFirst("initialize")).isNull();
        assertThat(parameters.getFirst("login")).isNull();
        assertThat(uriComponents.getPathSegments()).containsSequence("courses", courseId.toString(), "exercises", exerciseId.toString());

        this.checkExceptions();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void launchAsExistingStudent() throws Exception {

        var nowIn20Minutes = ZonedDateTime.now().plus(20, ChronoUnit.MINUTES);

        // Mock that student1 already exists since 20 min
        doReturn(nowIn20Minutes).when(timeService).now();

        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        URI header = request.post("/api/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        var uriComponents = UriComponentsBuilder.fromUri(header).build();
        MultiValueMap<String, String> parameters = UriComponentsBuilder.fromUri(header).build().getQueryParams();
        assertThat(parameters.getFirst("jwt")).isNotBlank();
        assertThat(parameters.getFirst("initialize")).isNull();
        assertThat(parameters.getFirst("login")).isNull();
        assertThat(uriComponents.getPathSegments()).containsSequence("courses", courseId.toString(), "exercises", exerciseId.toString());

        this.checkExceptions();

        Mockito.reset(timeService);
    }

    private void checkExceptions() throws Exception {
        request.postWithoutLocation("/api/lti/launch/" + programmingExercise.getId() + 1, requestBody, HttpStatus.NOT_FOUND, new HttpHeaders());

        doThrow(ArtemisAuthenticationException.class).when(ltiService).handleLaunchRequest(any(), any());
        request.postWithoutLocation("/api/lti/launch/" + programmingExercise.getId(), requestBody, HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders());

        doReturn("error").when(ltiService).verifyRequest(any());
        request.postWithoutLocation("/api/lti/launch/" + programmingExercise.getId(), requestBody, HttpStatus.UNAUTHORIZED, new HttpHeaders());
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
        ConfigUtil.testWithChangedConfig(ltiResource, "LTI_OAUTH_KEY", Optional.empty(), () -> {
            request.get("/api/lti/configuration/" + programmingExercise.getId(), HttpStatus.BAD_REQUEST, ExerciseLtiConfigurationDTO.class);
        });
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void exerciseLtiConfigurationAsStudent() throws Exception {
        course.setInstructorGroupName("123");
        courseRepository.save(course);
        request.get("/api/lti/configuration/" + programmingExercise.getId(), HttpStatus.FORBIDDEN, ExerciseLtiConfigurationDTO.class);
    }
}
