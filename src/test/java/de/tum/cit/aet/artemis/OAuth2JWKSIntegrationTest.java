package de.tum.cit.aet.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.OnlineCourseConfigurationRepository;
import de.tum.cit.aet.artemis.course.CourseFactory;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;

class OAuth2JWKSIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "oauth2jwksintegrationtest";

    @Autowired
    private OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    @Test
    @WithAnonymousUser
    void getKeysetIsPublicAndReturnsJson() throws Exception {

        String keyset = request.get("/.well-known/jwks.json", HttpStatus.OK, String.class);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonKeyset = objectMapper.readTree(keyset);
        assertThat(jsonKeyset.get("keys")).isNotNull();
    }

    @Test
    @WithAnonymousUser
    void getKeysetHasKey() throws Exception {
        Course course = new Course();
        course.setId(1L);
        courseRepository.save(course);
        OnlineCourseConfiguration onlineCourseConfiguration = CourseFactory.generateOnlineCourseConfiguration(course, "prefix", "url");
        LtiPlatformConfiguration ltiPlatformConfiguration = new LtiPlatformConfiguration();
        ltiPlatformConfiguration.setRegistrationId(TEST_PREFIX + "registrationId");
        ltiPlatformConfiguration.setClientId("clientId");
        ltiPlatformConfiguration.setAuthorizationUri("authUri");
        ltiPlatformConfiguration.setTokenUri("tokenUri");
        ltiPlatformConfiguration.setJwkSetUri("jwkUri");

        ltiPlatformConfigurationRepository.save(ltiPlatformConfiguration);
        onlineCourseConfigurationRepository.save(onlineCourseConfiguration);
        oAuth2JWKSService.updateKey(TEST_PREFIX + "registrationId");

        String keyset = request.get("/.well-known/jwks.json", HttpStatus.OK, String.class);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonKeyset = objectMapper.readTree(keyset);

        assertThat(jsonKeyset).isNotNull();
        JsonNode keys = jsonKeyset.get("keys");
        assertThat(keys).isNotNull();
    }
}
