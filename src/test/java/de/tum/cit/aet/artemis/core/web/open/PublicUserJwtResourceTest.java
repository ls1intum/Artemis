package de.tum.cit.aet.artemis.core.web.open;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.dto.OIDCCodeExchangeDTO;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PublicUserJwtResourceTest extends AbstractSpringIntegrationIndependentTest {

    private final MockMvc mockMvc;

    private final ObjectMapper objectMapper;

    @MockitoBean
    private RateLimitService rateLimitService;

    @Autowired
    public PublicUserJwtResourceTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void testExchangeCodeToJwtToken_whenOidcDisabled_returnsNotFound() throws Exception {
        OIDCCodeExchangeDTO requestDto = new OIDCCodeExchangeDTO("any-code", "any-verifier-12345678901234567890123456789012345");

        MvcResult result = mockMvc.perform(post("/api/core/public/exchange-code").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDto)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void testExchangeCodeToJwtToken_rateLimitExceeded_returnsTooManyRequests() throws Exception {
        doThrow(new RateLimitExceededException(60)).when(rateLimitService).enforcePerMinute(any(), eq(RateLimitType.AUTHENTICATION));

        OIDCCodeExchangeDTO requestDto = new OIDCCodeExchangeDTO("any-code", "any-verifier-12345678901234567890123456789012345");

        MvcResult result = mockMvc.perform(post("/api/core/public/exchange-code").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDto)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
