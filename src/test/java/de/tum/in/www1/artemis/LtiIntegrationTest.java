package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.LtiPlatformConfiguration;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class LtiIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "ltiintegrationtest";

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void init() {
        /* We mock the following method because we don't have the OAuth secret for edx */
        doReturn(null).when(lti10Service).verifyRequest(any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void dynamicRegistrationFailsAsStudent() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("openid_configuration", "configurationUrl");

        request.postWithoutResponseBody("/api/admin/lti13/dynamic-registration", HttpStatus.FORBIDDEN, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin1", roles = "ADMIN")
    void dynamicRegistrationFailsWithoutOpenIdConfiguration() throws Exception {
        request.postWithoutResponseBody("/api/admin/lti13/dynamic-registration", HttpStatus.BAD_REQUEST, new LinkedMultiValueMap<>());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getAllConfiguredLtiPlatformsAsAdmin() throws Exception {
        LtiPlatformConfiguration platform1 = new LtiPlatformConfiguration();
        platform1.setId(1L);
        fillLtiPlatformConfig(platform1);

        LtiPlatformConfiguration platform2 = new LtiPlatformConfiguration();
        platform1.setId(2L);
        fillLtiPlatformConfig(platform2);

        List<LtiPlatformConfiguration> expectedPlatforms = Arrays.asList(platform1, platform2);
        doReturn(expectedPlatforms).when(ltiPlatformConfigurationRepository).findAll();

        MvcResult mvcResult = request.getMvc().perform(get("/api/admin/lti-platforms")).andExpect(status().isOk()).andReturn();

        String jsonContent = mvcResult.getResponse().getContentAsString();
        List<LtiPlatformConfiguration> actualPlatforms = objectMapper.readValue(jsonContent, new TypeReference<>() {
            // Empty block intended for type inference by Jackson's ObjectMapper
        });

        assertThat(actualPlatforms).hasSize(expectedPlatforms.size());
        assertThat(actualPlatforms).usingRecursiveComparison().isEqualTo(expectedPlatforms);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void getAllConfiguredLtiPlatformsAsInstructor() throws Exception {
        request.get("/api/admin/lti-platforms", HttpStatus.FORBIDDEN, Object.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getLtiPlatformConfigurationByIdAsAdmin() throws Exception {
        Long platformId = 1L;
        LtiPlatformConfiguration expectedPlatform = new LtiPlatformConfiguration();
        expectedPlatform.setId(platformId);
        fillLtiPlatformConfig(expectedPlatform);

        doReturn(expectedPlatform).when(ltiPlatformConfigurationRepository).findByIdElseThrow(platformId);

        MvcResult mvcResult = request.getMvc().perform(get("/api/admin/lti-platform/{platformId}", platformId)).andExpect(status().isOk()).andReturn();

        String jsonContent = mvcResult.getResponse().getContentAsString();
        LtiPlatformConfiguration actualPlatform = objectMapper.readValue(jsonContent, LtiPlatformConfiguration.class);

        assertThat(actualPlatform).usingRecursiveComparison().isEqualTo(expectedPlatform);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void deleteLtiPlatformConfigurationByIdAsAdmin() throws Exception {
        Long platformId = 1L;
        doReturn(new LtiPlatformConfiguration()).when(ltiPlatformConfigurationRepository).findByIdElseThrow(platformId);
        doNothing().when(ltiPlatformConfigurationRepository).delete(any(LtiPlatformConfiguration.class));

        request.getMvc().perform(delete("/api/admin/lti-platform/{platformId}", platformId)).andExpect(status().isOk());

        verify(ltiPlatformConfigurationRepository).findByIdElseThrow(platformId);
        verify(ltiPlatformConfigurationRepository).delete(any(LtiPlatformConfiguration.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateLtiPlatformConfigurationAsAdmin() throws Exception {
        LtiPlatformConfiguration platformToUpdate = new LtiPlatformConfiguration();
        platformToUpdate.setId(1L);
        fillLtiPlatformConfig(platformToUpdate);

        request.getMvc().perform(put("/api/admin/lti-platform").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(platformToUpdate)))
                .andExpect(status().isOk());

        verify(ltiPlatformConfigurationRepository).save(platformToUpdate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3")
    void testRepositoryMethods() {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> ltiPlatformConfigurationRepository.findByIdElseThrow(Long.MAX_VALUE));

        assertThat(ltiPlatformConfigurationRepository.findByRegistrationId("")).isEqualTo(Optional.empty());
    }

    private void fillLtiPlatformConfig(LtiPlatformConfiguration ltiPlatformConfiguration) {
        ltiPlatformConfiguration.setRegistrationId("registrationId");
        ltiPlatformConfiguration.setClientId("clientId");
        ltiPlatformConfiguration.setAuthorizationUri("authUri");
        ltiPlatformConfiguration.setTokenUri("tokenUri");
        ltiPlatformConfiguration.setJwkSetUri("jwkUri");
    }
}
