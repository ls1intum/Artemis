package de.tum.in.www1.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.util.ReflectionTestUtils;

import com.nimbusds.jose.shaded.json.JSONObject;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.lti.Lti13TokenRetriever;
import de.tum.in.www1.artemis.service.connectors.lti.LtiDeepLinkingService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import uk.ac.ox.ctl.lti13.lti.Claims;

class LtiDeepLinkingServiceTest {

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private Lti13TokenRetriever tokenRetriever;

    private LtiDeepLinkingService ltiDeepLinkingService;

    private AutoCloseable closeable;

    private OidcIdToken oidcIdToken;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        oidcIdToken = mock(OidcIdToken.class);
        SecurityContextHolder.clearContext();
        ltiDeepLinkingService = new LtiDeepLinkingService(exerciseRepository, tokenRetriever);
        ReflectionTestUtils.setField(ltiDeepLinkingService, "artemisServerUrl", "http://artemis.com");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void testBuildLtiDeepLinkResponse() {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn("test_jwt");

        long exerciseId = 3;
        long courseId = 17;
        Exercise exercise = createMockExercise(exerciseId, courseId);
        when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));

        ltiDeepLinkingService.initializeDeepLinkingResponse(oidcIdToken, "test_registration_id");
        ltiDeepLinkingService.populateContentItems(String.valueOf(courseId), String.valueOf(exerciseId));

        String deepLinkResponse = ltiDeepLinkingService.buildLtiDeepLinkResponse();

        assertThat(deepLinkResponse).isNotNull();
        assertThat(deepLinkResponse).contains("test_jwt");
    }

    @Test
    void testEmptyJwtBuildLtiDeepLinkResponse() {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn(null);

        long exerciseId = 3;
        long courseId = 17;
        Exercise exercise = createMockExercise(exerciseId, courseId);
        when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));

        ltiDeepLinkingService.initializeDeepLinkingResponse(oidcIdToken, "test_registration_id");
        ltiDeepLinkingService.populateContentItems(String.valueOf(courseId), String.valueOf(exerciseId));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> ltiDeepLinkingService.buildLtiDeepLinkResponse())
                .withMessage("Deep linking response cannot be created")
                .matches(exception -> "LTI".equals(exception.getEntityName()) && "deepLinkingResponseFailed".equals(exception.getErrorKey()));
    }

    @Test
    void testEmptyReturnUrlBuildLtiDeepLinkResponse() {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn("test_jwt");

        long exerciseId = 3;
        long courseId = 17;
        Exercise exercise = createMockExercise(exerciseId, courseId);
        when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));

        ltiDeepLinkingService.initializeDeepLinkingResponse(oidcIdToken, "test_registration_id");
        ltiDeepLinkingService.populateContentItems(String.valueOf(courseId), String.valueOf(exerciseId));
        ltiDeepLinkingService.getLti13DeepLinkingResponse().setReturnUrl(null);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> ltiDeepLinkingService.buildLtiDeepLinkResponse()).withMessage("Cannot find platform return URL")
                .matches(exception -> "LTI".equals(exception.getEntityName()) && "deepLinkReturnURLEmpty".equals(exception.getErrorKey()));
    }

    @Test
    void testEmptyDeploymentIdBuildLtiDeepLinkResponse() {
        createMockOidcIdToken();
        when(tokenRetriever.createDeepLinkingJWT(anyString(), anyMap())).thenReturn("test_jwt");

        long exerciseId = 3;
        long courseId = 17;
        Exercise exercise = createMockExercise(exerciseId, courseId);
        when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));

        ltiDeepLinkingService.initializeDeepLinkingResponse(oidcIdToken, "test_registration_id");
        ltiDeepLinkingService.populateContentItems(String.valueOf(courseId), String.valueOf(exerciseId));
        ltiDeepLinkingService.getLti13DeepLinkingResponse().setDeploymentId(null);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> ltiDeepLinkingService.buildLtiDeepLinkResponse())
                .withMessage("Platform deployment id cannot be empty")
                .matches(exception -> "LTI".equals(exception.getEntityName()) && "deploymentIdEmpty".equals(exception.getErrorKey()));
    }

    @Test
    void testInitializeDeepLinkingResponse() {
        createMockOidcIdToken();

        ltiDeepLinkingService.initializeDeepLinkingResponse(oidcIdToken, "test_registration_id");

        assertThat(ltiDeepLinkingService).isNotNull();
        assertThat("test_registration_id").isEqualTo(ltiDeepLinkingService.getClientRegistrationId());
        assertThat(ltiDeepLinkingService.getLti13DeepLinkingResponse()).isNotNull();
    }

    @Test
    void testBadRequestForUninitializedDeepLinkingResponseInPopulateContentItems() {
        long exerciseId = 3;
        long courseId = 14;
        Exercise exercise = createMockExercise(exerciseId, courseId);

        when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDeepLinkingService.populateContentItems(String.valueOf(courseId), String.valueOf(exerciseId)));

    }

    @Test
    void testInitializeAndAddContentDeepLinkingResponse() {
        createMockOidcIdToken();

        long exerciseId = 3;
        long courseId = 17;
        Exercise exercise = createMockExercise(exerciseId, courseId);

        String targetUrl = "courses/" + courseId + "/exercise/" + exerciseId;
        when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));

        ltiDeepLinkingService.initializeDeepLinkingResponse(oidcIdToken, "test_registration_id");
        ltiDeepLinkingService.populateContentItems(String.valueOf(courseId), String.valueOf(exerciseId));

        assertThat(ltiDeepLinkingService).isNotNull();
        assertThat("test_registration_id").isEqualTo(ltiDeepLinkingService.getClientRegistrationId());
        assertThat(ltiDeepLinkingService.getLti13DeepLinkingResponse()).isNotNull();
        assertThat(ltiDeepLinkingService.getLti13DeepLinkingResponse()).isNotNull();
        assertThat(ltiDeepLinkingService.getLti13DeepLinkingResponse().getContentItems().contains("test_title"));
        assertThat(ltiDeepLinkingService.getLti13DeepLinkingResponse().getContentItems().contains(exercise.getType()));
        assertThat(ltiDeepLinkingService.getLti13DeepLinkingResponse().getContentItems().contains(targetUrl));
    }

    private void createMockOidcIdToken() {
        JSONObject mockSettings = new JSONObject();
        mockSettings.put("deep_link_return_url", "test_return_url");
        when(oidcIdToken.getClaim(Claims.DEEP_LINKING_SETTINGS)).thenReturn(mockSettings);

        when(oidcIdToken.getClaim("iss")).thenReturn("http://artemis.com");
        when(oidcIdToken.getClaim("aud")).thenReturn("http://moodle.com");
        when(oidcIdToken.getClaim("exp")).thenReturn("12345");
        when(oidcIdToken.getClaim("iat")).thenReturn("test");
        when(oidcIdToken.getClaim("nonce")).thenReturn("1234-34535-abcbcbd");
        when(oidcIdToken.getClaim(Claims.LTI_DEPLOYMENT_ID)).thenReturn("1");
    }

    private Exercise createMockExercise(long exerciseId, long courseId) {
        Exercise exercise = new TextExercise();
        exercise.setTitle("test_title");
        exercise.setId(exerciseId);

        Course course = new Course();
        course.setId(courseId);
        course.setOnlineCourseConfiguration(new OnlineCourseConfiguration());
        exercise.setCourse(course);
        return exercise;
    }
}
