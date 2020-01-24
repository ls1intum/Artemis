package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public abstract class AuthenticationIntegrationTest {

    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    protected ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected LtiUserIdRepository ltiUserIdRepository;

    @Autowired
    protected LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    @Autowired
    protected AuthorityRepository authorityRepository;

    @SpyBean
    protected LtiService ltiService;

    protected ProgrammingExercise programmingExercise;

    protected Course course;

    protected LtiLaunchRequestDTO ltiLaunchRequest;

    @BeforeEach
    public void setUp() {
        course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
        setupDefaultLtiLaunchRequest();
        doReturn(true).when(ltiService).verifyRequest(any());

        final var userAuthority = new Authority(AuthoritiesConstants.USER);
        final var instructorAuthority = new Authority(AuthoritiesConstants.INSTRUCTOR);
        final var adminAuthority = new Authority(AuthoritiesConstants.ADMIN);
        final var taAuthority = new Authority(AuthoritiesConstants.TEACHING_ASSISTANT);
        authorityRepository.saveAll(List.of(userAuthority, instructorAuthority, adminAuthority, taAuthority));
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithAnonymousUser
    public void launchLtiRequest_authViaEmail_success() throws Exception {
        ltiLaunchRequest.setCustom_lookup_user_by_email(true);
        request.postForm("/api/lti/launch/" + programmingExercise.getId(), ltiLaunchRequest, HttpStatus.FOUND);

        final var user = userRepository.findAll().get(0);
        final var ltiUser = ltiUserIdRepository.findAll().get(0);
        final var ltiOutcome = ltiOutcomeUrlRepository.findAll().get(0);
        assertThat(ltiUser.getUser()).isEqualTo(user);
        assertThat(ltiUser.getLtiUserId()).isEqualTo(ltiLaunchRequest.getUser_id());
        assertThat(ltiOutcome.getUser()).isEqualTo(user);
        assertThat(ltiOutcome.getExercise()).isEqualTo(programmingExercise);
        assertThat(ltiOutcome.getUrl()).isEqualTo(ltiLaunchRequest.getLis_outcome_service_url());
        assertThat(ltiOutcome.getSourcedId()).isEqualTo(ltiLaunchRequest.getLis_result_sourcedid());
    }

    private void setupDefaultLtiLaunchRequest() {
        ltiLaunchRequest = new LtiLaunchRequestDTO();
        ltiLaunchRequest.setContext_id("contextId123");
        ltiLaunchRequest.setContext_label("U4I");
        ltiLaunchRequest.setCustom_component_display_name("someDisplayName");
        ltiLaunchRequest.setCustom_lookup_user_by_email(false);
        ltiLaunchRequest.setCustom_require_existing_user(false);
        ltiLaunchRequest.setLaunch_presentation_locale("EN");
        ltiLaunchRequest.setLaunch_presentation_return_url("some.return.url.com");
        ltiLaunchRequest.setLis_outcome_service_url("some.outcome.service.url.com");
        ltiLaunchRequest.setLis_person_contact_email_primary("tester@tum.invalid");
        ltiLaunchRequest.setLis_person_sourcedid("somePersonSourceId");
        ltiLaunchRequest.setLis_result_sourcedid("someResultSourceId");
        ltiLaunchRequest.setLti_message_type("someMessageType");
        ltiLaunchRequest.setLti_version("1.0.0");
        ltiLaunchRequest.setOauth_consumer_key("artemis_lti_key");
        ltiLaunchRequest.setOauth_nonce("171298047571430710991572204884");
        ltiLaunchRequest.setOauth_signature("GYXApaIv0x7k/OPT9/oU38IBQRc=");
        ltiLaunchRequest.setOauth_signature_method("HMAC-SHA1");
        ltiLaunchRequest.setOauth_timestamp(1572204884L);
        ltiLaunchRequest.setOauth_version("1.0");
        ltiLaunchRequest.setResource_link_id("courses.u4i.com-16a90aca094448ab95caf484b5c35d32");
        ltiLaunchRequest.setRoles("Student");
        ltiLaunchRequest.setUser_id("ff30145d6884eeb2c1cef50298939383");
    }
}
