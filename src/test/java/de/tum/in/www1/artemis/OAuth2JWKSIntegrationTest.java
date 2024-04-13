package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.LtiPlatformConfiguration;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.OnlineCourseConfigurationRepository;

class OAuth2JWKSIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "oauth2jwksintegrationtest";

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    @Test
    @WithAnonymousUser
    void getKeysetIsPublicAndReturnsJson() throws Exception {

        String keyset = request.get("/.well-known/jwks.json", HttpStatus.OK, String.class);
        JsonObject jsonKeyset = JsonParser.parseString(keyset).getAsJsonObject();
        assertThat(jsonKeyset).isNotNull();
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
        JsonObject jsonKeyset = JsonParser.parseString(keyset).getAsJsonObject();
        assertThat(jsonKeyset).isNotNull();
        JsonElement keys = jsonKeyset.get("keys");
        assertThat(keys).isNotNull();
    }
}
