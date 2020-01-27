package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.LtiOutcomeUrl;
import de.tum.in.www1.artemis.domain.LtiUserId;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.LtiOutcomeUrlRepository;
import de.tum.in.www1.artemis.repository.LtiUserIdRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseLtiConfigurationDTO;

public class LtiIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    RequestUtilService request;

    @Autowired
    LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    @Autowired
    LtiUserIdRepository ltiUserIdRepository;

    private ProgrammingExercise programmingExercise;

    private String requestBody = "custom_component_display_name=Exercise" + "&lti_version=LTI-1p0" + "&oauth_nonce=171298047571430710991572204884"
            + "&resource_link_id=courses.edx.org-16a90aca094448ab95caf484b5c35d32" + "&context_id=course-v1%3ATUMx%2BSEECx%2B1T2018" + "&oauth_signature_method=HMAC-SHA1"
            + "&oauth_timestamp=1572204884" + "&custom_require_existing_user=false" + "&lis_person_contact_email_primary=anh.montag%40tum.de"
            + "&oauth_signature=GYXApaIv0x7k%2FOPT9%2FoU38IBQRc%3D" + "&context_title=Software+Engineering+Essentials" + "&lti_message_type=basic-lti-launch-request"
            + "&custom_lookup_user_by_email=false" + "&launch_presentation_return_url=" + "&context_label=TUMx" + "&user_id=ff30145d6884eeb2c1cef50298939383" + "&roles=Student"
            + "&oauth_version=1.0" + "&oauth_consumer_key=artemis_lti_key"
            + "&lis_result_sourcedid=course-v1%253ATUMx%252BSEECx%252B1T2018%3Acourses.edx.org-16a90aca094448ab95caf484b5c35d32%3Aff30145d6884eeb2c1cef50298939383"
            + "&launch_presentation_locale=en"
            + "&lis_outcome_service_url=https%3A%2F%2Fcourses.edx.org%2Fcourses%2Fcourse-v1%3ATUMx%2BSEECx%2B1T2018%2Fxblock%2Fblock-v1%3ATUMx%2BSEECx%2B1T2018%2Btype%40lti_consumer%2Bblock%4016a90aca094448ab95caf484b5c35d32%2Fhandler_noauth%2Foutcome_service_handler"
            + "&lis_person_sourcedid=lovaiible" + "&oauth_callback=about%3Ablank";

    @BeforeEach
    void init() throws InterruptedException {
        /* We mock the following methods because we don't have the OAuth secret for edx */
        doReturn(true).when(ltiService).verifyRequest(any());
        doNothing().when(ltiService).handleLaunchRequest(any(), any());

        database.addUsers(1, 1, 0);

        database.addCourseWithOneProgrammingExercise();
        programmingExercise = programmingExerciseRepository.findAll().get(0);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithAnonymousUser
    void launchAsAnonymousUser() throws Exception {
        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourse().getId();
        URI header = request.post("/api/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        assertTrue(header.toString().contains("?login&jwt="));
        assertTrue(header.toString().contains("/overview/" + courseId + "/exercises/" + exerciseId));

        this.checkExceptions();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void launchAsNewStudent() throws Exception {
        Long exerciseId = programmingExercise.getId();
        Long courseId = programmingExercise.getCourse().getId();
        URI header = request.post("/api/lti/launch/" + exerciseId, requestBody, HttpStatus.FOUND, MediaType.APPLICATION_FORM_URLENCODED, false);

        assertTrue(header.toString().contains("?welcome&jwt="));
        assertTrue(header.toString().contains("/overview/" + courseId + "/exercises/" + exerciseId));

        this.checkExceptions();
    }

    private void checkExceptions() throws Exception {
        request.postWithoutLocation("/api/lti/launch/" + programmingExercise.getId() + 1, requestBody, HttpStatus.NOT_FOUND, new HttpHeaders());

        doThrow(ArtemisAuthenticationException.class).when(ltiService).handleLaunchRequest(any(), any());
        request.postWithoutLocation("/api/lti/launch/" + programmingExercise.getId(), requestBody, HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders());

        doReturn(false).when(ltiService).verifyRequest(any());
        request.postWithoutLocation("/api/lti/launch/" + programmingExercise.getId(), requestBody, HttpStatus.UNAUTHORIZED, new HttpHeaders());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    void exerciseLtiConfiguration() throws Exception {
        request.get("/api/lti/configuration/" + programmingExercise.getId(), HttpStatus.OK, ExerciseLtiConfigurationDTO.class);
        request.get("/api/lti/configuration/" + programmingExercise.getId() + 1, HttpStatus.NOT_FOUND, ExerciseLtiConfigurationDTO.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void createLtiOutcomeUrl() throws Exception {
        List<LtiOutcomeUrl> returnedLtiOutcomeUrlList = ltiOutcomeUrlRepository.findAll();
        assertThat(returnedLtiOutcomeUrlList).isEmpty();

        LtiOutcomeUrl ltiOutcomeUrl = new LtiOutcomeUrl();
        request.postWithoutLocation("/api/lti-outcome-urls", ltiOutcomeUrl, HttpStatus.CREATED, new HttpHeaders());
        returnedLtiOutcomeUrlList = ltiOutcomeUrlRepository.findAll();
        assertThat(returnedLtiOutcomeUrlList).hasSize(1);

        ltiOutcomeUrl.setId(1L);
        request.postWithoutLocation("/api/lti-outcome-urls", ltiOutcomeUrl, HttpStatus.BAD_REQUEST, new HttpHeaders());
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void updateLtiOutcomeUrl() throws Exception {
        LtiOutcomeUrl ltiOutcomeUrl = new LtiOutcomeUrl();
        LtiOutcomeUrl returnedLtiOutcomeUrl = request.putWithResponseBody("/api/lti-outcome-urls", ltiOutcomeUrl, LtiOutcomeUrl.class, HttpStatus.CREATED);

        request.putWithResponseBody("/api/lti-outcome-urls", returnedLtiOutcomeUrl, LtiOutcomeUrl.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void getAllLtiOutcomeUrls() throws Exception {
        List<LtiOutcomeUrl> ltiOutcomeUrlList = request.getList("/api/lti-outcome-urls", HttpStatus.OK, LtiOutcomeUrl.class);
        assertThat(ltiOutcomeUrlList).as("List of LtiOutcomeUrl is empty").isEmpty();

        request.postWithoutLocation("/api/lti-outcome-urls", new LtiOutcomeUrl(), HttpStatus.CREATED, new HttpHeaders());
        ltiOutcomeUrlList = request.getList("/api/lti-outcome-urls", HttpStatus.OK, LtiOutcomeUrl.class);
        assertThat(ltiOutcomeUrlList).hasSize(1);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void getLtiOutcomeUrl() throws Exception {
        LtiOutcomeUrl ltiOutcomeUrl = new LtiOutcomeUrl();
        request.postWithoutLocation("/api/lti-outcome-urls", ltiOutcomeUrl, HttpStatus.CREATED, new HttpHeaders());
        LtiOutcomeUrl savedLtiOutcomeUrl = ltiOutcomeUrlRepository.findAll().get(0);

        request.get("/api/lti-outcome-urls/" + savedLtiOutcomeUrl.getId(), HttpStatus.OK, LtiOutcomeUrl.class);
        request.get("/api/lti-outcome-urls/" + savedLtiOutcomeUrl.getId() + 1, HttpStatus.NOT_FOUND, LtiOutcomeUrl.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void deleteLtiOutcomeUrl() throws Exception {
        LtiOutcomeUrl ltiOutcomeUrl = new LtiOutcomeUrl();
        request.postWithoutLocation("/api/lti-outcome-urls", ltiOutcomeUrl, HttpStatus.CREATED, new HttpHeaders());
        LtiOutcomeUrl savedLtiOutcomeUrl = ltiOutcomeUrlRepository.findAll().get(0);

        request.delete("/api/lti-outcome-urls/" + savedLtiOutcomeUrl.getId(), HttpStatus.OK);
        request.delete("/api/lti-outcome-urls/" + savedLtiOutcomeUrl.getId(), HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(ltiOutcomeUrlRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void createLtiUserId() throws Exception {
        List<LtiUserId> returnedLtiUserList = ltiUserIdRepository.findAll();
        assertThat(returnedLtiUserList).isEmpty();

        LtiUserId ltiUserId = new LtiUserId();
        request.postWithoutLocation("/api/lti-user-ids", ltiUserId, HttpStatus.CREATED, new HttpHeaders());
        returnedLtiUserList = ltiUserIdRepository.findAll();
        assertThat(returnedLtiUserList).hasSize(1);

        ltiUserId.setId(1L);
        request.postWithoutLocation("/api/lti-user-ids", ltiUserId, HttpStatus.BAD_REQUEST, new HttpHeaders());
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void updateLtiUserId() throws Exception {
        LtiUserId ltiUserId = new LtiUserId();
        LtiUserId returnedLtiUserId = request.putWithResponseBody("/api/lti-user-ids", ltiUserId, LtiUserId.class, HttpStatus.CREATED);

        request.putWithResponseBody("/api/lti-user-ids", returnedLtiUserId, LtiUserId.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void getLtiUserId() throws Exception {
        LtiUserId ltiUserId = new LtiUserId();
        request.putWithResponseBody("/api/lti-user-ids", ltiUserId, LtiUserId.class, HttpStatus.CREATED);
        LtiUserId savedLtiUserId = ltiUserIdRepository.findAll().get(0);

        request.get("/api/lti-user-ids/" + savedLtiUserId.getId(), HttpStatus.OK, LtiUserId.class);
        request.get("/api/lti-user-ids/" + savedLtiUserId.getId() + 1, HttpStatus.NOT_FOUND, LtiUserId.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void deleteLtiUserId() throws Exception {
        LtiUserId ltiUserId = new LtiUserId();
        request.putWithResponseBody("/api/lti-user-ids", ltiUserId, LtiUserId.class, HttpStatus.CREATED);
        LtiUserId savedLtiUserId = ltiUserIdRepository.findAll().get(0);

        request.delete("/api/lti-user-ids/" + savedLtiUserId.getId(), HttpStatus.OK);
        request.delete("/api/lti-user-ids/" + savedLtiUserId.getId() + 1, HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(ltiUserIdRepository.findAll()).isEmpty();
    }
}
