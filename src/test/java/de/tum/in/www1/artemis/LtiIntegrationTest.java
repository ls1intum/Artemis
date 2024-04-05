package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.Hibernate;
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

class LtiIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "ltiintegrationtest";

    @Autowired
    ObjectMapper objectMapper;

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

        MvcResult mvcResult = request.performMvcRequest(get("/api/lti-platforms")).andExpect(status().isOk()).andReturn();

        String jsonContent = mvcResult.getResponse().getContentAsString();
        List<LtiPlatformConfiguration> actualPlatforms = objectMapper.readValue(jsonContent, new TypeReference<>() {
            // Empty block intended for type inference by Jackson's ObjectMapper
        });

        assertThat(actualPlatforms).hasSize(expectedPlatforms.size());
        assertThat(actualPlatforms).usingRecursiveComparison().isEqualTo(expectedPlatforms);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "STUDENT")
    void getAllConfiguredLtiPlatformsAsStudent() throws Exception {
        request.get("/api/lti-platforms", HttpStatus.FORBIDDEN, Object.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getLtiPlatformConfigurationByIdAsAdmin() throws Exception {
        Long platformId = 1L;
        LtiPlatformConfiguration expectedPlatform = new LtiPlatformConfiguration();
        expectedPlatform.setId(platformId);
        fillLtiPlatformConfig(expectedPlatform);

        doReturn(expectedPlatform).when(ltiPlatformConfigurationRepository).findByIdElseThrow(platformId);

        MvcResult mvcResult = request.performMvcRequest(get("/api/admin/lti-platform/{platformId}", platformId)).andExpect(status().isOk()).andReturn();

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

        request.performMvcRequest(delete("/api/admin/lti-platform/{platformId}", platformId)).andExpect(status().isOk());

        verify(ltiPlatformConfigurationRepository).findByIdElseThrow(platformId);
        verify(ltiPlatformConfigurationRepository).delete(any(LtiPlatformConfiguration.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateLtiPlatformConfigurationAsAdmin() throws Exception {
        LtiPlatformConfiguration platformToUpdate = new LtiPlatformConfiguration();
        platformToUpdate.setId(1L);
        fillLtiPlatformConfig(platformToUpdate);

        request.performMvcRequest(put("/api/admin/lti-platform").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(platformToUpdate)))
                .andExpect(status().isOk());

        verify(ltiPlatformConfigurationRepository).save(platformToUpdate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void createNewLtiPlatformConfigurationAsAdmin() throws Exception {
        LtiPlatformConfiguration platformToCreate = new LtiPlatformConfiguration();

        fillLtiPlatformConfig(platformToCreate);
        platformToCreate.setRegistrationId(null);

        request.performMvcRequest(post("/api/admin/lti-platform").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(platformToCreate)))
                .andExpect(status().isOk());

        verify(ltiPlatformConfigurationRepository).save(any());

        Optional<LtiPlatformConfiguration> addedLtiPlatform = ltiPlatformConfigurationRepository.findByClientId(platformToCreate.getClientId());
        assertThat(addedLtiPlatform.isPresent()).isTrue();
        assertThat(addedLtiPlatform.get().getRegistrationId()).isNotNull();
        assertThat(addedLtiPlatform.get().getAuthorizationUri()).isEqualTo(platformToCreate.getAuthorizationUri());
        assertThat(addedLtiPlatform.get().getJwkSetUri()).isEqualTo(platformToCreate.getJwkSetUri());
        assertThat(addedLtiPlatform.get().getTokenUri()).isEqualTo(platformToCreate.getTokenUri());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testFindByRegistrationId() {
        assertThat(ltiPlatformConfigurationRepository.findByRegistrationId("nonExistingId")).isEqualTo(Optional.empty());

        LtiPlatformConfiguration newPlatformConfiguration = new LtiPlatformConfiguration();
        fillLtiPlatformConfig(newPlatformConfiguration);
        ltiPlatformConfigurationRepository.save(newPlatformConfiguration);

        assertThat(ltiPlatformConfigurationRepository.findByRegistrationId(newPlatformConfiguration.getRegistrationId())).isEqualTo(Optional.of(newPlatformConfiguration));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testFindByIdElseThrow() {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> ltiPlatformConfigurationRepository.findByIdElseThrow(Long.MAX_VALUE));

        LtiPlatformConfiguration newPlatformConfiguration = new LtiPlatformConfiguration();
        fillLtiPlatformConfig(newPlatformConfiguration);
        LtiPlatformConfiguration savedPlatformConfiguration = ltiPlatformConfigurationRepository.save(newPlatformConfiguration);

        assertThat(ltiPlatformConfigurationRepository.findByIdElseThrow(savedPlatformConfiguration.getId())).isEqualTo(savedPlatformConfiguration);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testFindLtiPlatformConfigurationWithEagerLoadedCoursesByIdElseThrow() {
        LtiPlatformConfiguration newPlatformConfiguration = new LtiPlatformConfiguration();
        fillLtiPlatformConfig(newPlatformConfiguration);
        LtiPlatformConfiguration savedPlatformConfiguration = ltiPlatformConfigurationRepository.save(newPlatformConfiguration);

        LtiPlatformConfiguration fetchedPlatformConfiguration = ltiPlatformConfigurationRepository
                .findLtiPlatformConfigurationWithEagerLoadedCoursesByIdElseThrow(savedPlatformConfiguration.getId());

        assertThat(fetchedPlatformConfiguration).isEqualTo(savedPlatformConfiguration);
        assertThat(Hibernate.isInitialized(fetchedPlatformConfiguration.getOnlineCourseConfigurations())).isTrue();
    }

    private void fillLtiPlatformConfig(LtiPlatformConfiguration ltiPlatformConfiguration) {
        ltiPlatformConfiguration.setRegistrationId("registrationId");
        ltiPlatformConfiguration.setClientId("platform-" + UUID.randomUUID());
        ltiPlatformConfiguration.setAuthorizationUri("authUri");
        ltiPlatformConfiguration.setTokenUri("tokenUri");
        ltiPlatformConfiguration.setJwkSetUri("jwkUri");
    }
}
