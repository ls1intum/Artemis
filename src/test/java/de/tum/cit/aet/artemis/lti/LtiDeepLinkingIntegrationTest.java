package de.tum.cit.aet.artemis.lti;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer;
import de.tum.cit.aet.artemis.lti.dto.Claims;
import de.tum.cit.aet.artemis.lti.service.DeepLinkingType;
import io.jsonwebtoken.Jwts;

class LtiDeepLinkingIntegrationTest extends AbstractLtiIntegrationTest {

    private static final String TEST_PREFIX = "ltideeplinkingintegrationtest";

    private Course course;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        var user = userTestRepository.findUserWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        user.setInternal(false);
        userTestRepository.save(user);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        course.setOnlineCourse(true);
        courseUtilService.addOnlineCourseConfigurationToCourse(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deepLinkingFailsAsStudent() throws Exception {
        var params = getDeepLinkingRequestParamsForExercise();

        request.postWithoutResponseBody("/api/lti/lti13/deep-linking/" + course.getId(), HttpStatus.FORBIDDEN, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deepLinkingFailsWithoutContentIdAndType() throws Exception {
        request.postWithoutResponseBody("/api/lti/lti13/deep-linking/" + course.getId(), HttpStatus.BAD_REQUEST, new LinkedMultiValueMap<>());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deepLinkingFailsWithoutContentIdForExercise() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resourceType", DeepLinkingType.EXERCISE.toString());
        params.add("ltiIdToken", createJwtForTest());
        params.add("clientRegistrationId", "registration-id");

        request.postWithoutResponseBody("/api/lti/lti13/deep-linking/" + course.getId(), HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deepLinkingFailsWithoutContentIdForLecture() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resourceType", DeepLinkingType.LECTURE.toString());
        params.add("ltiIdToken", createJwtForTest());
        params.add("clientRegistrationId", "registration-id");

        request.postWithoutResponseBody("/api/lti/lti13/deep-linking/" + course.getId(), HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deepLinkingFailsForNonOnlineCourse() throws Exception {
        course.setOnlineCourse(false);
        course.setOnlineCourseConfiguration(null);
        courseRepository.save(course);

        var params = getDeepLinkingRequestParamsForExercise();

        request.postWithoutResponseBody("/api/lti/lti13/deep-linking/" + course.getId(), HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deepLinkingAsInstructor() throws Exception {
        String jwkJsonString = """
                {
                  "kty": "RSA",
                  "d": "base64-encoded-value",
                  "e": "AQAB",
                  "use": "sig",
                  "kid": "123456",
                  "alg": "RS256",
                  "n": "base64-encoded-value"
                }
                """;
        doReturn(JWK.parse(jwkJsonString)).when(oAuth2JWKSService).getJWK(any());
        var params = getDeepLinkingRequestParamsForExercise();

        request.postWithoutResponseBody("/api/lti/lti13/deep-linking/" + course.getId(), HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deepLinkingSuccessAsInstructor() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        RSAKey mockRsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).privateKey((RSAPrivateKey) keyPair.getPrivate()).build();

        doReturn(mockRsaKey).when(oAuth2JWKSService).getJWK(any());
        var params = getDeepLinkingRequestParamsForExercise();

        request.postWithoutResponseBody("/api/lti/lti13/deep-linking/" + course.getId(), HttpStatus.OK, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deepLinkingFailsWithInvalidResourceType() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resourceType", "INVALID_RESOURCE_TYPE");
        params.add("ltiIdToken", createJwtForTest());
        params.add("clientRegistrationId", "registration-id");

        request.postWithoutResponseBody("/api/lti/lti13/deep-linking/" + course.getId(), HttpStatus.BAD_REQUEST, params);
    }

    private LinkedMultiValueMap<String, String> getDeepLinkingRequestParamsForExercise() {
        var params = new LinkedMultiValueMap<String, String>();
        Set<Long> exerciseIds = new HashSet<>();
        var exercise = course.getExercises().stream().findFirst().orElseThrow();
        exerciseIds.add(exercise.getId());

        String exerciseIdsParam = exerciseIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        params.add("resourceType", DeepLinkingType.EXERCISE.toString());
        params.add("contentIds", exerciseIdsParam);
        params.add("ltiIdToken", createJwtForTest());
        params.add("clientRegistrationId", "registration-id");
        return params;
    }

    private String createJwtForTest() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        Map<String, Object> claims = prepareTokenClaims();
        return Jwts.builder().claims(claims).signWith(key, Jwts.SIG.HS256).compact();
    }

    private Map<String, Object> prepareTokenClaims() {
        Map<String, Object> claims = new HashMap<>();

        addUserClaims(claims);
        addLTISpecificClaims(claims);
        addContextClaim(claims);
        addToolPlatformClaim(claims);
        addLaunchPresentationClaim(claims);
        addCustomClaim(claims);
        addDeepLinkingSettingsClaim(claims);

        return claims;
    }

    private void addUserClaims(Map<String, Object> claims) {
        claims.put("iss", "https://platform.example.org");
        claims.put("sub", "a6d5c443-1f51-4783-ba1a-7686ffe3b54a");
        claims.put("aud", List.of("962fa4d8-bcbf-49a0-94b2-2de05ad274af"));
        claims.put("exp", new Date(System.currentTimeMillis() + 3600_000)); // Token is valid for 1 hour
        claims.put("iat", new Date(System.currentTimeMillis()));
        claims.put("azp", "962fa4d8-bcbf-49a0-94b2-2de05ad274af");
        claims.put("nonce", "fc5fdc6d-5dd6-47f4-b2c9-5d1216e9b771");
        claims.put("name", "Ms Jane Marie Doe");
        claims.put("given_name", "Jane");
        claims.put("family_name", "Doe");
        claims.put("middle_name", "Marie");
        claims.put("picture", "https://example.org/jane.jpg");
        claims.put("email", "jane@example.org");
        claims.put("locale", "en-US");
    }

    private void addLTISpecificClaims(Map<String, Object> claims) {
        claims.put(Claims.LTI_DEPLOYMENT_ID, "07940580-b309-415e-a37c-914d387c1150");
        claims.put(Claims.MESSAGE_TYPE, CustomLti13Configurer.LTI13_DEEPLINK_MESSAGE_REQUEST);
        claims.put(Claims.LTI_VERSION, "1.3.0");
        claims.put(Claims.ROLES,
                Arrays.asList("http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor", "http://purl.imsglobal.org/vocab/lis/v2/institution/person#Faculty"));

    }

    private void addContextClaim(Map<String, Object> claims) {
        Map<String, Object> contextClaim = new HashMap<>();
        contextClaim.put("id", "c1d887f0-a1a3-4bca-ae25-c375edcc131a");
        contextClaim.put("label", "ECON 101");
        contextClaim.put("title", "Economics as a Social Science");
        contextClaim.put("type", List.of("CourseOffering"));
        claims.put(Claims.CONTEXT, contextClaim);
    }

    private void addToolPlatformClaim(Map<String, Object> claims) {
        Map<String, Object> toolPlatformClaim = new HashMap<>();
        toolPlatformClaim.put("contact_email", "support@example.org");
        toolPlatformClaim.put("description", "An Example Tool Platform");
        toolPlatformClaim.put("name", "Example Tool Platform");
        toolPlatformClaim.put("url", "https://example.org");
        toolPlatformClaim.put("product_family_code", "example.org");
        toolPlatformClaim.put("version", "1.0");
        claims.put(Claims.PLATFORM_INSTANCE, toolPlatformClaim);
    }

    private void addLaunchPresentationClaim(Map<String, Object> claims) {
        Map<String, Object> launchPresentationClaim = new HashMap<>();
        launchPresentationClaim.put("document_target", "iframe");
        launchPresentationClaim.put("height", 320);
        launchPresentationClaim.put("width", 240);
        claims.put(Claims.LAUNCH_PRESENTATION, launchPresentationClaim);
    }

    private void addCustomClaim(Map<String, Object> claims) {
        Map<String, Object> customClaim = new HashMap<>();
        customClaim.put("myCustom", "123");
        claims.put(Claims.CUSTOM, customClaim);
    }

    private void addDeepLinkingSettingsClaim(Map<String, Object> claims) {
        Map<String, Object> deepLinkingSettingsClaim = new HashMap<>();
        deepLinkingSettingsClaim.put("deep_link_return_url", "https://platform.example/deep_links");
        deepLinkingSettingsClaim.put("accept_types", Arrays.asList("link", "file", "html", "ltiResourceLink", "image"));
        deepLinkingSettingsClaim.put("accept_media_types", "image/*,text/html");
        deepLinkingSettingsClaim.put("accept_presentation_document_targets", Arrays.asList("iframe", "window", "embed"));
        deepLinkingSettingsClaim.put("accept_multiple", true);
        deepLinkingSettingsClaim.put("auto_create", true);
        deepLinkingSettingsClaim.put("title", "This is the default title");
        deepLinkingSettingsClaim.put("text", "This is the default text");
        deepLinkingSettingsClaim.put("data", "csrftoken:c7fbba78-7b75-46e3-9201-11e6d5f36f53");
        claims.put(Claims.DEEP_LINKING_SETTINGS, deepLinkingSettingsClaim);
    }
}
