package de.tum.cit.aet.artemis.core.web.open;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.dto.OIDCCodeExchangeDTO;
import de.tum.cit.aet.artemis.account.service.OIDCExchangeCodeService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

class PublicUserJwtResourceOIDCIntegrationTest extends AbstractSpringIntegrationLocalVCSamlTest {

    private static final String VALID_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk_valid_verifier";

    @Autowired
    private OIDCExchangeCodeService oidcExchangeCodeService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void testExchangeCodeToJwtToken_success() throws Exception {
        String challenge = OIDCExchangeCodeService.computeSHA256Challenge(VALID_VERIFIER);
        String expectedJwt = "mock.jwt.token.string";
        String exchangeCode = oidcExchangeCodeService.storeJwtAndGenerateCode(expectedJwt, challenge);

        OIDCCodeExchangeDTO requestDto = new OIDCCodeExchangeDTO(exchangeCode, VALID_VERIFIER);

        mockMvc.perform(post("/api/core/public/exchange-code").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(content().string(expectedJwt));
    }

    @Test
    void testExchangeCodeToJwtToken_notFoundForInvalidCode() throws Exception {
        OIDCCodeExchangeDTO requestDto = new OIDCCodeExchangeDTO("invalid-or-expired-code", VALID_VERIFIER);

        mockMvc.perform(post("/api/core/public/exchange-code").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testExchangeCodeToJwtToken_notFoundForInvalidVerifier() throws Exception {
        String challenge = OIDCExchangeCodeService.computeSHA256Challenge(VALID_VERIFIER);
        String expectedJwt = "mock.jwt.token.string";
        String exchangeCode = oidcExchangeCodeService.storeJwtAndGenerateCode(expectedJwt, challenge);

        String invalidVerifier = "wrong_verifier_1234567890123456789012345678901234567890";
        OIDCCodeExchangeDTO requestDto = new OIDCCodeExchangeDTO(exchangeCode, invalidVerifier);

        mockMvc.perform(post("/api/core/public/exchange-code").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testExchangeCodeToJwtToken_missingVerifier_returnsNotFound() throws Exception {
        String challenge = OIDCExchangeCodeService.computeSHA256Challenge(VALID_VERIFIER);
        String expectedJwt = "mock.jwt.token.string";
        String exchangeCode = oidcExchangeCodeService.storeJwtAndGenerateCode(expectedJwt, challenge);

        OIDCCodeExchangeDTO requestDto = new OIDCCodeExchangeDTO(exchangeCode, null);

        mockMvc.perform(post("/api/core/public/exchange-code").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testExchangeCodeToJwtToken_successWithSnakeCaseJson() throws Exception {
        String challenge = OIDCExchangeCodeService.computeSHA256Challenge(VALID_VERIFIER);
        String expectedJwt = "mock.jwt.token.string";
        String exchangeCode = oidcExchangeCodeService.storeJwtAndGenerateCode(expectedJwt, challenge);

        String rawSnakeCaseJson = """
                {
                    "code": "%s",
                    "code_verifier": "%s"
                }
                """.formatted(exchangeCode, VALID_VERIFIER);

        mockMvc.perform(post("/api/core/public/exchange-code").contentType(MediaType.APPLICATION_JSON).content(rawSnakeCaseJson)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(content().string(expectedJwt));
    }

    @Test
    void testExchangeCodeToJwtToken_codeIsSingleUse() throws Exception {
        String challenge = OIDCExchangeCodeService.computeSHA256Challenge(VALID_VERIFIER);
        String expectedJwt = "single-use.jwt.token";
        String exchangeCode = oidcExchangeCodeService.storeJwtAndGenerateCode(expectedJwt, challenge);

        OIDCCodeExchangeDTO requestDto = new OIDCCodeExchangeDTO(exchangeCode, VALID_VERIFIER);

        mockMvc.perform(post("/api/core/public/exchange-code").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk()).andExpect(content().string(expectedJwt));

        mockMvc.perform(post("/api/core/public/exchange-code").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound());
    }
}
