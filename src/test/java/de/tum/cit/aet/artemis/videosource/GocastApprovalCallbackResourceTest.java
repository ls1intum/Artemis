package de.tum.cit.aet.artemis.videosource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.dto.GocastApprovalResultDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastIntegrationException;

class GocastApprovalCallbackResourceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "gocastcallback";

    private static final String CALLBACK_PATH = "/api/videosource/public/gocast/approval/callback";

    @Autowired
    private MockMvc mockMvc;

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.addEmptyCourse();
        userUtilService.addInstructorToCourse(TEST_PREFIX + "instructor1", course);
        when(gocastBindingService.completeApproval("state", "code")).thenReturn(new GocastApprovalResultDTO(false, null));
    }

    @Test
    void publicCallbackIsCredentialFreeAndReturnsOnlyGenericContentWhenApprovalDoesNotComplete() throws Exception {
        var response = performCallback().andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn().getResponse();

        assertThat(response.getContentAsString()).contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">").contains("Connection not completed")
                .doesNotContain("course-management");

        mockMvc.perform(get(CALLBACK_PATH)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void publicCallbackUsesTheRequestedGermanLocaleForGenericErrors() throws Exception {
        var response = mockMvc.perform(get(CALLBACK_PATH).header(HttpHeaders.ACCEPT_LANGUAGE, "de")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn().getResponse();

        assertThat(response.getContentAsString()).contains("<html lang=\"de\">").contains("Verbindung nicht abgeschlossen").doesNotContain("course-management");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void authenticatedCourseInstructorReturnsToManagement() throws Exception {
        when(gocastBindingService.completeApproval("state", "code")).thenReturn(new GocastApprovalResultDTO(true, course.getId()));

        performCallback().andExpect(status().isSeeOther()).andExpect(redirectedUrl("/course-management/" + course.getId() + "/gocast-binding"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void successfulApproverWithoutArtemisSessionGetsGenericConfirmation() throws Exception {
        when(gocastBindingService.completeApproval("state", "code")).thenReturn(new GocastApprovalResultDTO(true, course.getId()));

        var response = mockMvc.perform(get(CALLBACK_PATH).param("state", "state").param("code", "code").header(HttpHeaders.ACCEPT_LANGUAGE, "de")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn().getResponse();

        assertThat(response.getContentAsString()).contains("<html lang=\"de\">").contains("Verbindung hergestellt").doesNotContain(Long.toString(course.getId()));
    }

    @Test
    void accessDeniedClearsOnlyTheMatchingLocalAttemptAndReturnsGenericContent() throws Exception {
        var response = mockMvc.perform(get(CALLBACK_PATH).param("state", "state").param("error", "access_denied")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn().getResponse();

        verify(gocastBindingService).cancelApproval("state");
        assertThat(response.getContentAsString()).contains("Connection not completed").doesNotContain("course-management");
    }

    @Test
    void upstreamFailureReturnsSafeGenericPageWithPrivacyHeaders() throws Exception {
        when(gocastBindingService.completeApproval("state", "code"))
                .thenThrow(new GocastIntegrationException("safe message", HttpStatus.BAD_GATEWAY, new IllegalStateException("secret body")));

        var response = performCallback().andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn().getResponse();

        assertThat(response.getContentAsString()).contains("Connection not completed").doesNotContain("secret body");
    }

    @Test
    void unexpectedDatabaseFailureKeepsPrivacyHeadersOnTheActualHttpErrorResponse() throws Exception {
        when(gocastBindingService.completeApproval("state", "code")).thenThrow(new DataAccessResourceFailureException("database unavailable"));

        performCallback().andExpect(status().is5xxServerError()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    private ResultActions performCallback() throws Exception {
        return mockMvc.perform(get(CALLBACK_PATH).param("state", "state").param("code", "code"));
    }
}
