package de.tum.cit.aet.artemis.core.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

/**
 * Simulates WebAuthn client (browser) operations for integration testing.
 * <p>
 * This utility generates valid WebAuthn registration and authentication responses
 * that would normally be created by a browser's navigator.credentials API and an
 * authenticator device. It enables true end-to-end testing of passkey functionality
 * without requiring actual hardware authenticators.
 * </p>
 * <p>
 * The simulator implements:
 * <ul>
 * <li>ES256 (ECDSA P-256) key pair generation</li>
 * <li>COSE public key encoding</li>
 * <li>ClientDataJSON creation for registration and authentication</li>
 * <li>AuthenticatorData creation with proper flags</li>
 * <li>AttestationObject creation with "none" attestation</li>
 * <li>Signature generation for authentication assertions</li>
 * </ul>
 *
 * @see <a href="https://www.w3.org/TR/webauthn-2/">WebAuthn Level 2 Specification</a>
 */
@Service
@Profile(SPRING_PROFILE_TEST)
public class WebAuthnClientSimulator {

    /** ES256 algorithm identifier in COSE format */
    private static final int COSE_ALG_ES256 = -7;

    /** COSE key type for EC2 (Elliptic Curve) */
    private static final int COSE_KTY_EC2 = 2;

    /** COSE curve identifier for P-256 */
    private static final int COSE_CRV_P256 = 1;

    /** Size of the credential ID in bytes */
    private static final int CREDENTIAL_ID_LENGTH = 32;

    /** Authenticator data flags */
    private static final byte FLAG_USER_PRESENT = 0x01;

    private static final byte FLAG_USER_VERIFIED = 0x04;

    private static final byte FLAG_ATTESTED_CREDENTIAL_DATA = 0x40;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ObjectMapper cborMapper = new ObjectMapper(new CBORFactory());

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Represents a virtual authenticator with its key pair and credential ID.
     * This holds all the data needed to simulate authenticator operations.
     */
    public record VirtualAuthenticator(byte[] credentialId, KeyPair keyPair, long signatureCount) {

        /**
         * Returns the credential ID as a base64url-encoded string.
         */
        public String getCredentialIdBase64Url() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(credentialId);
        }

        /**
         * Returns a new VirtualAuthenticator with an incremented signature count.
         */
        public VirtualAuthenticator withIncrementedCount() {
            return new VirtualAuthenticator(credentialId, keyPair, signatureCount + 1);
        }
    }

    /**
     * Represents a registration response that can be sent to the server.
     */
    public record RegistrationResponse(String id, String rawId, String type, RegistrationResponseData response, String authenticatorAttachment) {

    }

    /**
     * The response data for registration.
     */
    public record RegistrationResponseData(String clientDataJSON, String attestationObject, String authenticatorData, String publicKey, int publicKeyAlgorithm,
            List<String> transports) {
    }

    /**
     * Represents an authentication response that can be sent to the server.
     */
    public record AuthenticationResponse(String id, String rawId, String type, AuthenticationResponseData response, String authenticatorAttachment) {
    }

    /**
     * The response data for authentication.
     */
    public record AuthenticationResponseData(String clientDataJSON, String authenticatorData, String signature, String userHandle) {
    }

    /**
     * Creates a new virtual authenticator with a fresh key pair and credential ID.
     *
     * @return a new VirtualAuthenticator instance
     */
    public VirtualAuthenticator createVirtualAuthenticator() {
        try {
            // Generate credential ID
            byte[] credentialId = new byte[CREDENTIAL_ID_LENGTH];
            secureRandom.nextBytes(credentialId);

            // Generate ES256 (ECDSA P-256) key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(new ECGenParameterSpec("secp256r1"), secureRandom);
            KeyPair keyPair = keyGen.generateKeyPair();

            return new VirtualAuthenticator(credentialId, keyPair, 0);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to create virtual authenticator", e);
        }
    }

    /**
     * Creates a registration response simulating what a browser would return from
     * navigator.credentials.create().
     *
     * @param authenticator the virtual authenticator to use
     * @param challenge     the challenge from the server (base64url-encoded)
     * @param origin        the origin URL (e.g., "https://localhost:9000")
     * @param rpId          the relying party ID (e.g., "localhost")
     * @return a RegistrationResponse ready to be serialized and sent to the server
     */
    public RegistrationResponse createRegistrationResponse(VirtualAuthenticator authenticator, String challenge, String origin, String rpId) {
        try {
            // Create clientDataJSON
            byte[] clientDataJSON = createClientDataJSON("webauthn.create", challenge, origin);
            String clientDataJSONBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(clientDataJSON);

            // Create authenticator data with attested credential data
            byte[] authenticatorData = createAuthenticatorDataForRegistration(rpId, authenticator);
            String authenticatorDataBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(authenticatorData);

            // Create attestation object (none format)
            byte[] attestationObject = createAttestationObject(authenticatorData);
            String attestationObjectBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(attestationObject);

            // Encode public key in COSE format
            byte[] publicKeyCose = encodePublicKeyCose((ECPublicKey) authenticator.keyPair().getPublic());
            String publicKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKeyCose);

            String credentialIdBase64 = authenticator.getCredentialIdBase64Url();

            RegistrationResponseData responseData = new RegistrationResponseData(clientDataJSONBase64, attestationObjectBase64, authenticatorDataBase64, publicKeyBase64,
                    COSE_ALG_ES256, List.of("internal", "hybrid"));

            return new RegistrationResponse(credentialIdBase64, credentialIdBase64, "public-key", responseData, "platform");
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to create registration response", e);
        }
    }

    /**
     * Creates an authentication response simulating what a browser would return from
     * navigator.credentials.get().
     *
     * @param authenticator the virtual authenticator to use (should be one that was previously registered)
     * @param challenge     the challenge from the server (base64url-encoded)
     * @param origin        the origin URL (e.g., "https://localhost:9000")
     * @param rpId          the relying party ID (e.g., "localhost")
     * @param userHandle    the user handle (user ID as bytes, base64url-encoded)
     * @return an AuthenticationResponse ready to be serialized and sent to the server
     */
    public AuthenticationResponse createAuthenticationResponse(VirtualAuthenticator authenticator, String challenge, String origin, String rpId, String userHandle) {
        try {
            // Create clientDataJSON
            byte[] clientDataJSON = createClientDataJSON("webauthn.get", challenge, origin);
            String clientDataJSONBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(clientDataJSON);

            // Create authenticator data for authentication (no attested credential data)
            byte[] authenticatorData = createAuthenticatorDataForAuthentication(rpId, authenticator.signatureCount());
            String authenticatorDataBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(authenticatorData);

            // Create signature over authenticatorData || SHA-256(clientDataJSON)
            byte[] signature = createSignature(authenticatorData, clientDataJSON, (ECPrivateKey) authenticator.keyPair().getPrivate());
            String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

            String credentialIdBase64 = authenticator.getCredentialIdBase64Url();

            AuthenticationResponseData responseData = new AuthenticationResponseData(clientDataJSONBase64, authenticatorDataBase64, signatureBase64, userHandle);

            return new AuthenticationResponse(credentialIdBase64, credentialIdBase64, "public-key", responseData, "platform");
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to create authentication response", e);
        }
    }

    /**
     * Converts a user ID (Long) to base64url-encoded format as expected by WebAuthn.
     *
     * @param userId the user ID
     * @return base64url-encoded user ID
     */
    public String encodeUserHandle(Long userId) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(userId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    /**
     * Creates a clientDataJSON structure.
     *
     * @param type      "webauthn.create" for registration, "webauthn.get" for authentication
     * @param challenge the server-provided challenge (base64url-encoded)
     * @param origin    the origin URL
     * @return the clientDataJSON as bytes
     */
    private byte[] createClientDataJSON(String type, String challenge, String origin) throws IOException {
        ClientData clientData = new ClientData(type, challenge, origin, false);
        return objectMapper.writeValueAsBytes(clientData);
    }

    /**
     * Record representing the clientDataJSON structure.
     */
    private record ClientData(String type, String challenge, String origin, boolean crossOrigin) {
    }

    /**
     * Creates authenticator data for registration (includes attested credential data).
     * <p>
     * Structure (37+ bytes):
     * - rpIdHash (32 bytes): SHA-256 hash of rpId
     * - flags (1 byte): UP, UV, AT, ED flags
     * - signCount (4 bytes): Signature counter, big-endian
     * - attestedCredentialData (variable): AAGUID + credentialId length + credentialId + COSE public key
     * </p>
     */
    private byte[] createAuthenticatorDataForRegistration(String rpId, VirtualAuthenticator authenticator) throws NoSuchAlgorithmException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // rpIdHash (32 bytes)
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] rpIdHash = sha256.digest(rpId.getBytes(StandardCharsets.UTF_8));
        baos.write(rpIdHash);

        // flags (1 byte): UP + UV + AT (attested credential data present)
        byte flags = (byte) (FLAG_USER_PRESENT | FLAG_USER_VERIFIED | FLAG_ATTESTED_CREDENTIAL_DATA);
        baos.write(flags);

        // signCount (4 bytes, big-endian)
        baos.write(intToBytes((int) authenticator.signatureCount()));

        // Attested credential data
        // AAGUID (16 bytes) - all zeros for software authenticator
        baos.write(new byte[16]);

        // Credential ID length (2 bytes, big-endian)
        baos.write((authenticator.credentialId().length >> 8) & 0xFF);
        baos.write(authenticator.credentialId().length & 0xFF);

        // Credential ID
        baos.write(authenticator.credentialId());

        // COSE public key
        byte[] coseKey = encodePublicKeyCose((ECPublicKey) authenticator.keyPair().getPublic());
        baos.write(coseKey);

        return baos.toByteArray();
    }

    /**
     * Creates authenticator data for authentication (no attested credential data).
     * <p>
     * Structure (37 bytes):
     * - rpIdHash (32 bytes): SHA-256 hash of rpId
     * - flags (1 byte): UP, UV flags
     * - signCount (4 bytes): Signature counter, big-endian
     * </p>
     */
    private byte[] createAuthenticatorDataForAuthentication(String rpId, long signatureCount) throws NoSuchAlgorithmException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // rpIdHash (32 bytes)
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] rpIdHash = sha256.digest(rpId.getBytes(StandardCharsets.UTF_8));
        try {
            baos.write(rpIdHash);

            // flags (1 byte): UP + UV (no attested credential data)
            byte flags = (byte) (FLAG_USER_PRESENT | FLAG_USER_VERIFIED);
            baos.write(flags);

            // signCount (4 bytes, big-endian)
            baos.write(intToBytes((int) signatureCount));
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to create authenticator data", e);
        }

        return baos.toByteArray();
    }

    /**
     * Creates an attestation object with "none" format (no attestation).
     * <p>
     * CBOR structure:
     * {
     * "fmt": "none",
     * "authData": <authenticatorData>,
     * "attStmt": {}
     * }
     * </p>
     * Uses Jackson CBOR for encoding since the keys are all strings.
     */
    private byte[] createAttestationObject(byte[] authenticatorData) throws IOException {
        // Using LinkedHashMap to preserve key order (important for some implementations)
        Map<String, Object> attestationObject = new LinkedHashMap<>();
        attestationObject.put("fmt", "none");
        attestationObject.put("authData", authenticatorData);
        attestationObject.put("attStmt", new LinkedHashMap<>()); // Empty map

        return cborMapper.writeValueAsBytes(attestationObject);
    }

    /**
     * Encodes an EC public key in COSE format for ES256.
     * <p>
     * COSE Key structure for EC2:
     * {
     * 1 (kty): 2 (EC2),
     * 3 (alg): -7 (ES256),
     * -1 (crv): 1 (P-256),
     * -2 (x): <x coordinate>,
     * -3 (y): <y coordinate>
     * }
     * </p>
     * Manual CBOR encoding is used since COSE requires integer keys,
     * which Jackson CBOR doesn't support directly.
     */
    private byte[] encodePublicKeyCose(ECPublicKey publicKey) throws IOException {
        byte[] x = toUnsignedByteArray(publicKey.getW().getAffineX().toByteArray(), 32);
        byte[] y = toUnsignedByteArray(publicKey.getW().getAffineY().toByteArray(), 32);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // CBOR map with 5 items (major type 5, additional info 5)
        baos.write(0xA5);

        // Key 1 (kty): Value 2 (EC2)
        baos.write(0x01); // unsigned int 1
        baos.write(0x02); // unsigned int 2

        // Key 3 (alg): Value -7 (ES256)
        baos.write(0x03); // unsigned int 3
        baos.write(0x26); // negative int -7 (encoded as 0x20 | 6)

        // Key -1 (crv): Value 1 (P-256)
        baos.write(0x20); // negative int -1 (encoded as 0x20 | 0)
        baos.write(0x01); // unsigned int 1

        // Key -2 (x): Value x coordinate (byte string)
        baos.write(0x21); // negative int -2 (encoded as 0x20 | 1)
        baos.write(0x58); // byte string, 1-byte length follows
        baos.write(0x20); // length 32
        baos.write(x);

        // Key -3 (y): Value y coordinate (byte string)
        baos.write(0x22); // negative int -3 (encoded as 0x20 | 2)
        baos.write(0x58); // byte string, 1-byte length follows
        baos.write(0x20); // length 32
        baos.write(y);

        return baos.toByteArray();
    }

    /**
     * Creates an ECDSA signature over authenticatorData || SHA-256(clientDataJSON).
     */
    private byte[] createSignature(byte[] authenticatorData, byte[] clientDataJSON, ECPrivateKey privateKey) throws Exception {
        // Calculate SHA-256 hash of clientDataJSON
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] clientDataHash = sha256.digest(clientDataJSON);

        // Concatenate authenticatorData || clientDataHash
        byte[] signedData = new byte[authenticatorData.length + clientDataHash.length];
        System.arraycopy(authenticatorData, 0, signedData, 0, authenticatorData.length);
        System.arraycopy(clientDataHash, 0, signedData, authenticatorData.length, clientDataHash.length);

        // Sign with ECDSA
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(signedData);
        return signature.sign();
    }

    /**
     * Converts an integer to a 4-byte big-endian array.
     */
    private byte[] intToBytes(int value) {
        return new byte[] { (byte) ((value >> 24) & 0xFF), (byte) ((value >> 16) & 0xFF), (byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF) };
    }

    /**
     * Normalizes a BigInteger byte array to a fixed length.
     * BigInteger may include an extra leading zero byte for sign, or may be shorter than expected.
     */
    private byte[] toUnsignedByteArray(byte[] bytes, int length) {
        if (bytes.length == length) {
            return bytes;
        }
        else if (bytes.length == length + 1 && bytes[0] == 0) {
            // Remove leading zero byte
            byte[] result = new byte[length];
            System.arraycopy(bytes, 1, result, 0, length);
            return result;
        }
        else if (bytes.length < length) {
            // Pad with leading zeros
            byte[] result = new byte[length];
            System.arraycopy(bytes, 0, result, length - bytes.length, bytes.length);
            return result;
        }
        else {
            throw new IllegalArgumentException("Byte array too long: " + bytes.length + " > " + length);
        }
    }
}
