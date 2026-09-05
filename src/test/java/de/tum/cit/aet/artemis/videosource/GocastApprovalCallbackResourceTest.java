package de.tum.cit.aet.artemis.videosource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.dto.GocastApprovalResultDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingService;
import de.tum.cit.aet.artemis.videosource.web.GocastApprovalCallbackResource;

class GocastApprovalCallbackResourceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MessageSource messageSource;

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void publicCallbackIsCredentialFreeAndReturnsOnlyGenericContentWhenDisabled() throws Exception {
        var response = mockMvc.perform(get("/api/videosource/public/gocast/approval/callback").param("state", "state").param("requestId", "request").param("code", "code"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn()
                .getResponse();

        assertThat(response.getContentAsString()).contains("Connection not completed").doesNotContain("course-management");

        mockMvc.perform(get("/api/videosource/public/gocast/approval/callback")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void publicCallbackUsesTheRequestedGermanLocaleForGenericErrors() throws Exception {
        var response = mockMvc.perform(get("/api/videosource/public/gocast/approval/callback").header(HttpHeaders.ACCEPT_LANGUAGE, "de")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn().getResponse();

        assertThat(response.getContentAsString()).contains("<html lang=\"de\">").contains("Verbindung nicht abgeschlossen").doesNotContain("course-management");
    }

    @Test
    @WithMockUser(username = "gocastcallbackinstructor", roles = "INSTRUCTOR")
    void authenticatedCourseInstructorReturnsToManagement() {
        GocastBindingService bindingService = mock(GocastBindingService.class);
        AuthorizationCheckService authorization = mock(AuthorizationCheckService.class);
        when(bindingService.completeApproval("request", "state", "code")).thenReturn(new GocastApprovalResultDTO(true, 37L));
        when(authorization.isAtLeastInstructorInCourse(37L)).thenReturn(true);
        var resource = new GocastApprovalCallbackResource(Optional.of(bindingService), authorization, messageSource);

        var response = resource.completeApproval("state", "request", "code");

        assertThat(response.getStatusCode().value()).isEqualTo(303);
        assertThat(response.getHeaders().getLocation()).hasToString("/course-management/37/gocast-binding");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void successfulApproverWithoutArtemisSessionGetsGenericConfirmation() {
        GocastBindingService bindingService = mock(GocastBindingService.class);
        AuthorizationCheckService authorization = mock(AuthorizationCheckService.class);
        when(bindingService.completeApproval("request", "state", "code")).thenReturn(new GocastApprovalResultDTO(true, 37L));
        var resource = new GocastApprovalCallbackResource(Optional.of(bindingService), authorization, messageSource);

        LocaleContextHolder.setLocale(Locale.GERMAN);

        var response = resource.completeApproval("state", "request", "code");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("<html lang=\"de\">").contains("Verbindung hergestellt").doesNotContain("37");
    }

    @Test
    void upstreamFailureReturnsSafeGenericPageWithPrivacyHeaders() {
        GocastBindingService bindingService = mock(GocastBindingService.class);
        AuthorizationCheckService authorization = mock(AuthorizationCheckService.class);
        when(bindingService.completeApproval("request", "state", "code")).thenThrow(new de.tum.cit.aet.artemis.videosource.service.GocastIntegrationException("safe message",
                org.springframework.http.HttpStatus.BAD_GATEWAY, new IllegalStateException("secret body")));
        var resource = new GocastApprovalCallbackResource(Optional.of(bindingService), authorization, messageSource);

        var response = resource.completeApproval("state", "request", "code");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getBody()).contains("Connection not completed").doesNotContain("secret body");
    }
}
