package de.tum.in.www1.artemis.usermanagement.web.rest.errors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import de.tum.in.www1.artemis.usermanagement.IntegrationTest;
import de.tum.in.www1.artemis.util.RequestUtilService;

/**
 * Integration tests {@link ExceptionTranslator} controller advice.
 */
@IntegrationTest
@WithMockUser
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis" })
@TestPropertySource(properties = { "artemis.user-management.use-external=false" })
class ExceptionTranslatorTest {

    @Autowired
    protected RequestUtilService request;

    @Test
    void testMethodArgumentNotValid() throws Exception {
        request.getMvc().perform(post("/api/exception-translator-test/method-argument").content("{}").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(jsonPath("$.message").value(ErrorConstants.ERR_VALIDATION))
                .andExpect(jsonPath("$.fieldErrors.[0].objectName").value("test")).andExpect(jsonPath("$.fieldErrors.[0].field").value("test"))
                .andExpect(jsonPath("$.fieldErrors.[0].message").value("must not be null"));
    }

    @Test
    void testMissingServletRequestPartException() throws Exception {
        ResultActions perform = request.getMvc().perform(get("/api/exception-translator-test/missing-servlet-request-part")).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    void testMissingServletRequestParameterException() throws Exception {
        ResultActions perform = request.getMvc().perform(get("/api/exception-translator-test/missing-servlet-request-parameter")).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    void testAccessDenied() throws Exception {
        ResultActions perform = request.getMvc().perform(get("/api/exception-translator-test/access-denied")).andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(jsonPath("$.message").value("error.http.403"))
                .andExpect(jsonPath("$.detail").value("test access denied!"));
    }

    @Test
    void testUnauthorized() throws Exception {
        ResultActions perform = request.getMvc().perform(get("/api/exception-translator-test/unauthorized")).andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(jsonPath("$.message").value("error.http.401"))
                .andExpect(jsonPath("$.path").value("/api/exception-translator-test/unauthorized")).andExpect(jsonPath("$.detail").value("test authentication failed!"));
    }

    @Test
    void testMethodNotSupported() throws Exception {
        ResultActions perform = request.getMvc().perform(post("/api/exception-translator-test/access-denied")).andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(jsonPath("$.message").value("error.http.405"))
                .andExpect(jsonPath("$.detail").value("Request method 'POST' not supported"));
    }

    @Test
    void testExceptionWithResponseStatus() throws Exception {
        ResultActions perform = request.getMvc().perform(get("/api/exception-translator-test/response-status")).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(jsonPath("$.message").value("error.http.400"))
                .andExpect(jsonPath("$.title").value("test response status"));
    }

    @Test
    void testInternalServerError() throws Exception {
        ResultActions perform = request.getMvc().perform(get("/api/exception-translator-test/internal-server-error")).andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(jsonPath("$.message").value("error.http.500"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"));
    }
}
