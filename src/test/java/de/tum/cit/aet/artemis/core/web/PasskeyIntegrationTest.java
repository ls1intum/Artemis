package de.tum.cit.aet.artemis.core.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.PasskeyDTO;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.core.util.PasskeyCredentialUtilService;
import de.tum.cit.aet.artemis.core.util.WebAuthnClientSimulator;
import de.tum.cit.aet.artemis.core.util.WebAuthnClientSimulator.AuthenticationResponse;
import de.tum.cit.aet.artemis.core.util.WebAuthnClientSimulator.RegistrationResponse;
import de.tum.cit.aet.artemis.core.util.WebAuthnClientSimulator.VirtualAuthenticator;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration tests for WebAuthn/Passkey functionality.
 * <p>
 * Tests cover:
 * <ul>
 * <li>PasskeyResource endpoints (CRUD operations for passkeys)</li>
 * <li>WebAuthn challenge endpoints handled by Spring Security filters</li>
 * </ul>
 * <p>
 * Note: The WebAuthn endpoints (/webauthn/*) are handled by Spring Security filters,
 * not by explicit REST controllers. These filters are configured in
 * {@link de.tum.cit.aet.artemis.core.security.passkey.ArtemisWebAuthnConfigurer}.
 */
class PasskeyIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "passkeyintegration";

    @Autowired
    private PasskeyCredentialsRepository passkeyCredentialsRepository;

    @Autowired
    private PasskeyCredentialUtilService passkeyCredentialUtilService;

    @Autowired
    private WebAuthnClientSimulator webAuthnClientSimulator;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${server.url:http://localhost}")
    private String serverUrl;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
    }

    @AfterEach
    void cleanupPasskeys() {
        // Clean up only passkey credentials created for test users to avoid interfering with other tests
        User student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElse(null);
        User student2 = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElse(null);

        if (student1 != null) {
            passkeyCredentialsRepository.deleteAll(passkeyCredentialsRepository.findByUser(student1.getId()));
        }
        if (student2 != null) {
            passkeyCredentialsRepository.deleteAll(passkeyCredentialsRepository.findByUser(student2.getId()));
        }
    }

    // ==================== PasskeyResource Tests (CRUD Operations) ====================

    @Nested
    class GetPasskeysTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testGetPasskeys_Success() throws Exception {
            User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            PasskeyCredential credential1 = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);
            PasskeyCredential credential2 = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);

            List<PasskeyDTO> passkeys = request.getList("/api/core/passkey/user", HttpStatus.OK, PasskeyDTO.class);

            assertThat(passkeys).hasSize(2);
            assertThat(passkeys).extracting(PasskeyDTO::credentialId).containsExactlyInAnyOrder(credential1.getCredentialId(), credential2.getCredentialId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testGetPasskeys_EmptyList() throws Exception {
            List<PasskeyDTO> passkeys = request.getList("/api/core/passkey/user", HttpStatus.OK, PasskeyDTO.class);

            assertThat(passkeys).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testGetPasskeys_OnlyReturnsOwnPasskeys() throws Exception {
            User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");

            PasskeyCredential student1Credential = passkeyCredentialUtilService.createAndSavePasskeyCredential(student1);
            passkeyCredentialUtilService.createAndSavePasskeyCredential(student2);

            List<PasskeyDTO> passkeys = request.getList("/api/core/passkey/user", HttpStatus.OK, PasskeyDTO.class);

            assertThat(passkeys).hasSize(1);
            assertThat(passkeys.getFirst().credentialId()).isEqualTo(student1Credential.getCredentialId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "ANONYMOUS")
        void testGetPasskeys_AccessDeniedForAnonymous() throws Exception {
            request.getList("/api/core/passkey/user", HttpStatus.FORBIDDEN, PasskeyDTO.class);
        }
    }

    @Nested
    class UpdatePasskeyLabelTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testUpdatePasskeyLabel_Success() throws Exception {
            User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);
            PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), "newLabel", existingCredential.getCreatedDate(), existingCredential.getLastUsed(),
                    false);

            request.put("/api/core/passkey/" + modifiedCredential.credentialId(), modifiedCredential, HttpStatus.OK);

            PasskeyCredential modifiedCredentialInDatabase = passkeyCredentialsRepository.findByCredentialId(modifiedCredential.credentialId())
                    .orElseThrow(() -> new IllegalStateException("Credential not found"));

            assertThat(modifiedCredentialInDatabase.getCredentialId()).isEqualTo(existingCredential.getCredentialId());
            assertThat(modifiedCredentialInDatabase.getLabel()).isNotEqualTo(existingCredential.getLabel());
            assertThat(modifiedCredentialInDatabase.getLabel()).isEqualTo(modifiedCredential.label());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "ANONYMOUS")
        void testUpdatePasskeyLabel_AccessDeniedBecauseOfRole() throws Exception {
            User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);
            PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), "newLabel", existingCredential.getCreatedDate(), existingCredential.getLastUsed(),
                    false);

            request.put("/api/core/passkey/" + modifiedCredential.credentialId(), modifiedCredential, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testUpdatePasskeyLabel_NotFoundBecausePasskeyBelongsToSomebodyElse() throws Exception {
            User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
            PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(student2);
            PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), "newLabel", existingCredential.getCreatedDate(), existingCredential.getLastUsed(),
                    false);

            request.put("/api/core/passkey/" + modifiedCredential.credentialId(), modifiedCredential, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testUpdatePasskeyLabel_NotFound() throws Exception {
            User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            PasskeyCredential existingCredential = passkeyCredentialUtilService.createAndSavePasskeyCredential(student2);
            PasskeyDTO modifiedCredential = new PasskeyDTO(existingCredential.getCredentialId(), "newLabel", existingCredential.getCreatedDate(), existingCredential.getLastUsed(),
                    false);

            request.put("/api/core/passkey/" + modifiedCredential.credentialId() + "idDoesNotExist", modifiedCredential, HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeletePasskeyTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testDeletePasskey_Success() throws Exception {
            User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            PasskeyCredential credential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);

            assertThat(passkeyCredentialsRepository.findByCredentialId(credential.getCredentialId())).isPresent();

            request.delete("/api/core/passkey/" + credential.getCredentialId(), HttpStatus.NO_CONTENT);

            assertThat(passkeyCredentialsRepository.findByCredentialId(credential.getCredentialId())).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testDeletePasskey_NotFoundWhenDeletingOthersPasskey() throws Exception {
            User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
            PasskeyCredential credential = passkeyCredentialUtilService.createAndSavePasskeyCredential(student2);

            request.delete("/api/core/passkey/" + credential.getCredentialId(), HttpStatus.NOT_FOUND);

            // Verify credential still exists
            assertThat(passkeyCredentialsRepository.findByCredentialId(credential.getCredentialId())).isPresent();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testDeletePasskey_NotFoundForNonExistentCredential() throws Exception {
            request.delete("/api/core/passkey/nonExistentCredentialId", HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "ANONYMOUS")
        void testDeletePasskey_AccessDeniedForAnonymous() throws Exception {
            User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            PasskeyCredential credential = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);

            request.delete("/api/core/passkey/" + credential.getCredentialId(), HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testDeletePasskey_OnlyDeletesSpecifiedPasskey() throws Exception {
            User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            PasskeyCredential credential1 = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);
            PasskeyCredential credential2 = passkeyCredentialUtilService.createAndSavePasskeyCredential(user);

            request.delete("/api/core/passkey/" + credential1.getCredentialId(), HttpStatus.NO_CONTENT);

            assertThat(passkeyCredentialsRepository.findByCredentialId(credential1.getCredentialId())).isEmpty();
            assertThat(passkeyCredentialsRepository.findByCredentialId(credential2.getCredentialId())).isPresent();
        }
    }

    // ==================== WebAuthn Challenge Endpoint Tests ====================

    /**
     * Tests for the WebAuthn registration options endpoint.
     * <p>
     * This endpoint is handled by Spring Security's
     * {@link de.tum.cit.aet.artemis.core.security.passkey.ArtemisPublicKeyCredentialCreationOptionsFilter}
     * at POST /webauthn/register/options.
     * <p>
     * The endpoint returns a PublicKeyCredentialCreationOptions object containing:
     * - challenge: Random bytes for the registration ceremony
     * - rp: Relying Party information (id, name)
     * - user: User information
     * - pubKeyCredParams: Supported algorithms
     * - timeout: Registration timeout
     * - excludeCredentials: Already registered credentials
     * - authenticatorSelection: Authenticator requirements
     * - attestation: Attestation preference
     */
    @Nested
    class RegistrationOptionsTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testGetRegistrationOptions_Success() throws Exception {
            MockHttpServletResponse response = request.performMvcRequest(post("/webauthn/register/options").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                    .andReturn().getResponse();

            String responseBody = response.getContentAsString();

            // Verify the response contains expected WebAuthn registration options fields
            assertThat(responseBody).contains("challenge");
            assertThat(responseBody).contains("rp");
            assertThat(responseBody).contains("user");
            assertThat(responseBody).contains("pubKeyCredParams");
            assertThat(responseBody).contains("timeout");
        }

        @Test
        @WithAnonymousUser
        void testGetRegistrationOptions_BadRequestForAnonymous() throws Exception {
            // Registration options require an authenticated user to generate user-specific options
            // The filter returns 400 Bad Request when no user is authenticated
            request.performMvcRequest(post("/webauthn/register/options").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        }
    }

    /**
     * Tests for the WebAuthn authentication options endpoint.
     * <p>
     * This endpoint is handled by Spring Security's PublicKeyCredentialRequestOptionsFilter
     * at POST /webauthn/authenticate/options.
     * <p>
     * The endpoint returns a PublicKeyCredentialRequestOptions object containing:
     * - challenge: Random bytes for the authentication ceremony
     * - timeout: Authentication timeout
     * - rpId: Relying Party ID
     * - allowCredentials: Allowed credential descriptors (if user is known)
     * - userVerification: User verification requirement
     */
    @Nested
    class AuthenticationOptionsTests {

        @Test
        @WithAnonymousUser
        void testGetAuthenticationOptions_SuccessForAnonymous() throws Exception {
            // Authentication options should be available to anonymous users
            // as the user may not be logged in when starting the passkey login flow
            MockHttpServletResponse response = request.performMvcRequest(post("/webauthn/authenticate/options").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                    .andReturn().getResponse();

            String responseBody = response.getContentAsString();

            // Verify the response contains expected WebAuthn authentication options fields
            assertThat(responseBody).contains("challenge");
            assertThat(responseBody).contains("timeout");
            assertThat(responseBody).contains("rpId");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testGetAuthenticationOptions_SuccessForAuthenticatedUser() throws Exception {
            MockHttpServletResponse response = request.performMvcRequest(post("/webauthn/authenticate/options").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                    .andReturn().getResponse();

            String responseBody = response.getContentAsString();

            assertThat(responseBody).contains("challenge");
            assertThat(responseBody).contains("timeout");
            assertThat(responseBody).contains("rpId");
        }
    }

    /**
     * Tests for the WebAuthn login endpoint.
     * <p>
     * This endpoint is handled by Spring Security's
     * {@link de.tum.cit.aet.artemis.core.security.passkey.ArtemisWebAuthnAuthenticationFilter}
     * at POST /login/webauthn.
     * <p>
     * Note: Full passkey login flow cannot be tested in integration tests without
     * mocking the authenticator device. These tests verify the endpoint is reachable
     * and returns appropriate error responses for invalid requests.
     */
    @Nested
    class PasskeyLoginTests {

        @Test
        @WithAnonymousUser
        void testLoginWithPasskey_UnauthorizedForEmptyBody() throws Exception {
            // Spring Security's WebAuthn filter returns 401 Unauthorized when authentication fails
            request.performMvcRequest(post("/login/webauthn").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        }

        @Test
        @WithAnonymousUser
        void testLoginWithPasskey_UnauthorizedForInvalidCredential() throws Exception {
            // Spring Security's WebAuthn filter returns 401 Unauthorized for invalid credentials
            String invalidCredential = """
                    {
                        "id": "invalidId",
                        "rawId": "invalidRawId",
                        "type": "public-key",
                        "response": {
                            "authenticatorData": "invalid",
                            "clientDataJSON": "invalid",
                            "signature": "invalid"
                        }
                    }
                    """;

            request.performMvcRequest(post("/login/webauthn").contentType(MediaType.APPLICATION_JSON).content(invalidCredential)).andExpect(status().isUnauthorized());
        }
    }

    /**
     * Tests for the WebAuthn registration endpoint.
     * <p>
     * This endpoint is handled by Spring Security's
     * {@link de.tum.cit.aet.artemis.core.security.passkey.ArtemisWebAuthnRegistrationFilter}
     * at POST /webauthn/register.
     * <p>
     * Note: Full passkey registration flow cannot be tested in integration tests without
     * mocking the authenticator device. These tests verify the endpoint is reachable
     * and returns appropriate error responses for invalid requests.
     */
    @Nested
    class PasskeyRegistrationTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testRegisterPasskey_BadRequestForInvalidCredential() throws Exception {
            // First get registration options to initialize the registration ceremony
            request.performMvcRequest(post("/webauthn/register/options").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

            // Sending invalid credential data should result in bad request
            String invalidCredential = """
                    {
                        "publicKey": {
                            "credential": {
                                "id": "invalidId",
                                "rawId": "invalidRawId",
                                "type": "public-key",
                                "response": {
                                    "attestationObject": "invalid",
                                    "clientDataJSON": "invalid"
                                }
                            }
                        },
                        "label": "Test Passkey"
                    }
                    """;

            request.performMvcRequest(post("/webauthn/register").contentType(MediaType.APPLICATION_JSON).content(invalidCredential)).andExpect(status().isBadRequest());
        }

        @Test
        @WithAnonymousUser
        void testRegisterPasskey_UnauthorizedForAnonymous() throws Exception {
            request.performMvcRequest(post("/webauthn/register").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        }
    }

    // ==================== Full E2E WebAuthn Tests with Virtual Authenticator ====================

    /**
     * End-to-end tests for the complete passkey registration flow.
     * <p>
     * These tests use {@link WebAuthnClientSimulator} to simulate what a browser's
     * navigator.credentials.create() API would produce, enabling true end-to-end testing
     * of the passkey feature without requiring actual hardware authenticators.
     */
    @Nested
    class FullPasskeyRegistrationE2ETests {

        private String getRpId() throws Exception {
            java.net.URL url = new java.net.URI(serverUrl).toURL();
            return url.getHost();
        }

        private String getOrigin() {
            return serverUrl;
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testFullPasskeyRegistration_Success() throws Exception {
            User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            String rpId = getRpId();
            String origin = getOrigin();

            // Verify no passkeys exist initially
            assertThat(passkeyCredentialsRepository.findByUser(user.getId())).isEmpty();

            // Step 1: Get registration options from server
            MockHttpServletResponse optionsResponse = request.performMvcRequest(post("/webauthn/register/options").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn().getResponse();

            Map<String, Object> options = objectMapper.readValue(optionsResponse.getContentAsString(), new TypeReference<>() {
            });
            String challenge = (String) options.get("challenge");

            // Step 2: Create virtual authenticator and generate registration response
            VirtualAuthenticator authenticator = webAuthnClientSimulator.createVirtualAuthenticator();
            RegistrationResponse registrationResponse = webAuthnClientSimulator.createRegistrationResponse(authenticator, challenge, origin, rpId);

            // Step 3: Create registration request with label
            Map<String, Object> registrationRequest = Map.of("publicKey", Map.of("credential", registrationResponse, "label", "Test Passkey"));
            String requestBody = objectMapper.writeValueAsString(registrationRequest);

            // Step 4: Submit registration
            request.performMvcRequest(post("/webauthn/register").contentType(MediaType.APPLICATION_JSON).content(requestBody)).andExpect(status().isOk());

            // Step 5: Verify passkey was created in database
            List<PasskeyCredential> credentials = passkeyCredentialsRepository.findByUser(user.getId());
            assertThat(credentials).hasSize(1);
            assertThat(credentials.getFirst().getCredentialId()).isEqualTo(authenticator.getCredentialIdBase64Url());
            assertThat(credentials.getFirst().getLabel()).isEqualTo("Test Passkey");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testFullPasskeyRegistration_MultiplePasskeys() throws Exception {
            User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            String rpId = getRpId();
            String origin = getOrigin();

            // Register first passkey
            MockHttpServletResponse optionsResponse1 = request.performMvcRequest(post("/webauthn/register/options").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn().getResponse();

            Map<String, Object> options1 = objectMapper.readValue(optionsResponse1.getContentAsString(), new TypeReference<>() {
            });
            VirtualAuthenticator authenticator1 = webAuthnClientSimulator.createVirtualAuthenticator();
            RegistrationResponse response1 = webAuthnClientSimulator.createRegistrationResponse(authenticator1, (String) options1.get("challenge"), origin, rpId);

            request.performMvcRequest(post("/webauthn/register").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("publicKey", Map.of("credential", response1, "label", "First Passkey"))))).andExpect(status().isOk());

            // Register second passkey
            MockHttpServletResponse optionsResponse2 = request.performMvcRequest(post("/webauthn/register/options").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn().getResponse();

            Map<String, Object> options2 = objectMapper.readValue(optionsResponse2.getContentAsString(), new TypeReference<>() {
            });
            VirtualAuthenticator authenticator2 = webAuthnClientSimulator.createVirtualAuthenticator();
            RegistrationResponse response2 = webAuthnClientSimulator.createRegistrationResponse(authenticator2, (String) options2.get("challenge"), origin, rpId);

            request.performMvcRequest(post("/webauthn/register").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("publicKey", Map.of("credential", response2, "label", "Second Passkey"))))).andExpect(status().isOk());

            // Verify both passkeys exist
            List<PasskeyCredential> credentials = passkeyCredentialsRepository.findByUser(user.getId());
            assertThat(credentials).hasSize(2);
            assertThat(credentials).extracting(PasskeyCredential::getLabel).containsExactlyInAnyOrder("First Passkey", "Second Passkey");
        }
    }

    /**
     * End-to-end tests for the complete passkey authentication flow.
     * <p>
     * These tests use {@link WebAuthnClientSimulator} to simulate what a browser's
     * navigator.credentials.get() API would produce, enabling true end-to-end testing
     * of passkey login without requiring actual hardware authenticators.
     */
    @Nested
    class FullPasskeyAuthenticationE2ETests {

        private String getRpId() throws Exception {
            java.net.URL url = new java.net.URI(serverUrl).toURL();
            return url.getHost();
        }

        private String getOrigin() {
            return serverUrl;
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testFullPasskeyAuthentication_Success() throws Exception {
            User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            String rpId = getRpId();
            String origin = getOrigin();

            // Step 1: Register a passkey first
            MockHttpServletResponse regOptionsResponse = request.performMvcRequest(post("/webauthn/register/options").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn().getResponse();

            Map<String, Object> regOptions = objectMapper.readValue(regOptionsResponse.getContentAsString(), new TypeReference<>() {
            });
            VirtualAuthenticator authenticator = webAuthnClientSimulator.createVirtualAuthenticator();
            RegistrationResponse registrationResponse = webAuthnClientSimulator.createRegistrationResponse(authenticator, (String) regOptions.get("challenge"), origin, rpId);

            request.performMvcRequest(post("/webauthn/register").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("publicKey", Map.of("credential", registrationResponse, "label", "Auth Test Passkey")))))
                    .andExpect(status().isOk());

            // Verify passkey is registered
            List<PasskeyCredential> credentials = passkeyCredentialsRepository.findByUser(user.getId());
            assertThat(credentials).hasSize(1);

            // Verify the credential ID matches what we expect
            String expectedCredentialId = authenticator.getCredentialIdBase64Url();
            String storedCredentialId = credentials.getFirst().getCredentialId();
            assertThat(storedCredentialId).as("Stored credential ID should match the authenticator's credential ID").isEqualTo(expectedCredentialId);

            // Step 2: Get authentication options (using a shared session for the authentication flow)
            // The session is required because the challenge is stored in the session and validated on login
            MockHttpSession authSession = new MockHttpSession();
            MockHttpServletResponse authOptionsResponse = request
                    .performMvcRequest(post("/webauthn/authenticate/options").contentType(MediaType.APPLICATION_JSON).session(authSession)).andExpect(status().isOk()).andReturn()
                    .getResponse();

            Map<String, Object> authOptions = objectMapper.readValue(authOptionsResponse.getContentAsString(), new TypeReference<>() {
            });
            String authChallenge = (String) authOptions.get("challenge");

            // Step 3: Create authentication response using the same authenticator
            String userHandle = webAuthnClientSimulator.encodeUserHandle(user.getId());
            VirtualAuthenticator authenticatorWithIncrementedCount = authenticator.withIncrementedCount();
            AuthenticationResponse authResponse = webAuthnClientSimulator.createAuthenticationResponse(authenticatorWithIncrementedCount, authChallenge, origin, rpId, userHandle);

            // Step 4: Submit authentication (using the same session that has the challenge stored)
            // We need to set the requestedSessionId so the server can retrieve the stored challenge
            String authRequestBody = objectMapper.writeValueAsString(authResponse);
            MockHttpServletResponse loginResponse = request
                    .performMvcRequest(post("/login/webauthn").contentType(MediaType.APPLICATION_JSON).content(authRequestBody).session(authSession).with(mockRequest -> {
                        mockRequest.setRequestedSessionId(authSession.getId());
                        return mockRequest;
                    })).andExpect(status().isOk()).andReturn().getResponse();

            // Step 5: Verify successful authentication
            String responseBody = loginResponse.getContentAsString();
            assertThat(responseBody).contains("redirectUrl");

            // Verify JWT cookie was set
            assertThat(loginResponse.getHeader("Set-Cookie")).isNotNull();
            assertThat(loginResponse.getHeader("Set-Cookie")).contains("jwt");
        }

        @Test
        @WithAnonymousUser
        void testPasskeyAuthentication_FailsForUnregisteredCredential() throws Exception {
            String rpId = getRpId();
            String origin = getOrigin();

            // Create a virtual authenticator that was never registered
            VirtualAuthenticator unregisteredAuthenticator = webAuthnClientSimulator.createVirtualAuthenticator();

            // Get authentication options (using shared session for the authentication flow)
            MockHttpSession authSession = new MockHttpSession();
            MockHttpServletResponse authOptionsResponse = request
                    .performMvcRequest(post("/webauthn/authenticate/options").contentType(MediaType.APPLICATION_JSON).session(authSession)).andExpect(status().isOk()).andReturn()
                    .getResponse();

            Map<String, Object> authOptions = objectMapper.readValue(authOptionsResponse.getContentAsString(), new TypeReference<>() {
            });

            // Try to authenticate with unregistered credential
            AuthenticationResponse authResponse = webAuthnClientSimulator.createAuthenticationResponse(unregisteredAuthenticator, (String) authOptions.get("challenge"), origin,
                    rpId, "dummyUserHandle");

            // Authentication should fail (using same session with requestedSessionId set)
            request.performMvcRequest(post("/login/webauthn").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(authResponse)).session(authSession)
                    .with(mockRequest -> {
                        mockRequest.setRequestedSessionId(authSession.getId());
                        return mockRequest;
                    })).andExpect(status().isUnauthorized());
        }
    }
}
