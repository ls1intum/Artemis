package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.OnlineCourseConfigurationRepository;
import de.tum.in.www1.artemis.security.OAuth2JWKSService;
import de.tum.in.www1.artemis.util.ModelFactory;

class OAuth2JWKSIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    @Autowired
    private OAuth2JWKSService oAuth2JWKSService;

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

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
        OnlineCourseConfiguration onlineCourseConfiguration = ModelFactory.generateOnlineCourseConfiguration(course, "key", "secret", "prefix", "url");
        onlineCourseConfiguration.setRegistrationId("registrationId");
        onlineCourseConfiguration.setClientId("clientId");
        onlineCourseConfiguration.setAuthorizationUri("authUri");
        onlineCourseConfiguration.setTokenUri("tokenUri");

        onlineCourseConfigurationRepository.save(onlineCourseConfiguration);
        oAuth2JWKSService.updateKey("registrationId");

        String keyset = request.get("/.well-known/jwks.json", HttpStatus.OK, String.class);
        JsonObject jsonKeyset = JsonParser.parseString(keyset).getAsJsonObject();
        assertThat(jsonKeyset).isNotNull();
        JsonElement keys = jsonKeyset.get("keys");
        assertThat(keys).isNotNull();
        assertThat(keys.getAsJsonArray()).hasSize(1);
    }
}
