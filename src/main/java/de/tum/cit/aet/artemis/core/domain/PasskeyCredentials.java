package de.tum.cit.aet.artemis.core.domain;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.webauthn4j.data.AuthenticatorTransport;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey;
import com.webauthn4j.data.attestation.authenticator.EdDSACOSEKey;
import com.webauthn4j.data.attestation.authenticator.RSACOSEKey;
import com.webauthn4j.data.attestation.statement.AttestationStatement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.CertificateBaseAttestationStatement;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.client.CollectedClientData;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import com.webauthn4j.data.extension.client.AuthenticationExtensionsClientOutputs;
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput;
import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecord;
import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecordImpl;
import com.webauthn4j.util.Base64UrlUtil;

@Entity
@Table(name = "passkey_credentials")
public class PasskeyCredentials extends AbstractAuditingEntity {

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "credential_id", length = 1023, nullable = false, unique = true)
    private String credentialId;

    @Lob
    @Column(name = "public_key")
    private String publicKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "attestation_type", nullable = false)
    private AttestationType attestationType;

    @Column(name = "aaguid", length = 36, nullable = false, columnDefinition = "CHAR(36) DEFAULT '00000000-0000-0000-0000-000000000000'")
    private String aaGUID = "00000000-0000-0000-0000-000000000000";

    @Column(name = "signature_count")
    private Long signatureCount;

    @Column(name = "last_used_date")
    private Instant lastUsedDate;

    @Column(name = "type", length = 25)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "transports")
    private Transports transports;

    @Column(name = "backup_eligible", nullable = false)
    private boolean backupEligible = false;

    @Column(name = "backup_state", nullable = false)
    private boolean backupState = false;

    // Getters and Setters

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public AttestationType getAttestationType() {
        return attestationType;
    }

    public void setAttestationType(AttestationType attestationType) {
        this.attestationType = attestationType;
    }

    public String getAAGUID() {
        return aaGUID;
    }

    public void setAAGUID(String aaGUID) {
        this.aaGUID = aaGUID;
    }

    public Long getSignatureCount() {
        return signatureCount;
    }

    public void setSignatureCount(Long signatureCount) {
        this.signatureCount = signatureCount;
    }

    public Instant getLastUsedDate() {
        return lastUsedDate;
    }

    public void setLastUsedDate(Instant lastUsedDate) {
        this.lastUsedDate = lastUsedDate;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Transports getTransports() {
        return transports;
    }

    public void setTransports(Transports transports) {
        this.transports = transports;
    }

    public boolean getBackupEligible() {
        return backupEligible;
    }

    public void setBackupEligible(boolean backupEligible) {
        this.backupEligible = backupEligible;
    }

    public boolean getBackupState() {
        return backupState;
    }

    public void setBackupState(boolean backupState) {
        this.backupState = backupState;
    }

    public WebAuthnCredentialRecord toWebAuthnCredentialRecord() {
        // Assuming `name` is equivalent to the `credentialId` or another unique identifier
        String name = this.credentialId;

        // Assuming `userPrincipal` is represented by the `user` entity, which could be a user identifier or object
        String userPrincipal = this.user.getLogin(); // or another appropriate user identifier

        // Assuming `attestationStatement` is derived from the `attestationType` field
        // TODO: map enum to class
        AttestationStatement attestationStatement = new NoneAttestationStatement(); // Replace with appropriate instantiation

        // Use the existing fields for `uvInitialized`, `backupEligible`, and `backupState`
        boolean uvInitialized = true; // or derived from other logic
        boolean backupEligible = this.backupEligible;
        boolean backupState = this.backupState;

        // Use `signatureCount` as the `counter`
        long counter = this.signatureCount != null ? this.signatureCount : 0L;

        // Convert AAGUID string to AAGUID object
        AAGUID aaguid = new AAGUID(this.aaGUID.getBytes(StandardCharsets.UTF_8)); // or use a proper conversion

        // Convert credentialId string to byte[]
        byte[] credentialIdBytes = this.credentialId.getBytes(StandardCharsets.UTF_8);

        // Assuming publicKey is a string representation of a COSEKey, convert it to a COSEKey object
        COSEKey coseKey;

        try {
            // Convert publicKey from string to byte array
            byte[] publicKeyBytes = Base64.getDecoder().decode(this.publicKey);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);

            KeyFactory keyFactory;
            PublicKey publicKey = null;

            // Create the appropriate COSEKey based on the type
            switch (this.type) {
                case "EC2":
                    keyFactory = KeyFactory.getInstance("EC");
                    publicKey = keyFactory.generatePublic(keySpec);
                    coseKey = EC2COSEKey.create((ECPublicKey) publicKey, COSEAlgorithmIdentifier.ES256); // Assuming ES256 for EC2 keys
                    break;

                case "EdDSA":
                    keyFactory = KeyFactory.getInstance("EdDSA");
                    publicKey = keyFactory.generatePublic(keySpec);
                    coseKey = EdDSACOSEKey.create((EdECPublicKey) publicKey, COSEAlgorithmIdentifier.EdDSA); // Assuming EdDSA for EdDSA keys
                    break;

                case "RSA":
                    keyFactory = KeyFactory.getInstance("RSA");
                    publicKey = keyFactory.generatePublic(keySpec);
                    coseKey = RSACOSEKey.create((RSAPublicKey) publicKey, COSEAlgorithmIdentifier.RS256); // Assuming RS256 for RSA keys
                    break;

                default:
                    throw new IllegalArgumentException("Unknown key type: " + this.type);
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to create COSEKey", e);
        }

        // Assuming `attestedCredentialData` can be derived from `credentialId` and `publicKey`
        AttestedCredentialData attestedCredentialData = new AttestedCredentialData(aaguid, credentialIdBytes, coseKey);

        // Assuming these fields can be derived from the entity or other sources
        AuthenticationExtensionsAuthenticatorOutputs<RegistrationExtensionAuthenticatorOutput> authenticatorExtensions = null; // TODO: Replace with actual data
        CollectedClientData clientData = null; // TODO: Replace with actual data
        AuthenticationExtensionsClientOutputs<RegistrationExtensionClientOutput> clientExtensions = null; // TODO: Replace with actual data

        // Convert `Transports` enum to `Set<AuthenticatorTransport>`
        Set<AuthenticatorTransport> transports = Set.of(AuthenticatorTransport.create(this.transports.name())); // Replace with actual mapping logic

        return new WebAuthnCredentialRecordImpl(name, userPrincipal, attestationStatement, uvInitialized, backupEligible, backupState, counter, attestedCredentialData,
                authenticatorExtensions, clientData, clientExtensions, transports);
    }

    public static PasskeyCredentials fromWebAuthnCredentialRecord(WebAuthnCredentialRecord record, User user) {
        PasskeyCredentials passkeyCredentials = new PasskeyCredentials();

        // Set the credentialId
        var credentialIdString = Base64UrlUtil.encodeToString(record.getAttestedCredentialData().getCredentialId());
        passkeyCredentials.setCredentialId(credentialIdString);

        // Associate the user entity
        passkeyCredentials.setUser(user);

        // Convert the public key from COSEKey to a base64-encoded string
        COSEKey coseKey = record.getAttestedCredentialData().getCOSEKey();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(coseKey.getPublicKey().getEncoded());
        passkeyCredentials.setPublicKey(publicKeyBase64);

        // Set the attestationType based on the AttestationStatement
        passkeyCredentials.setAttestationType(convertAttestationStatementToType(record.getAttestationStatement()));

        // Set the AAGUID from the AttestedCredentialData
        String aaguid = Base64.getEncoder().encodeToString(record.getAttestedCredentialData().getAaguid().getBytes());
        passkeyCredentials.setAAGUID(aaguid);

        // Set the signature counter
        passkeyCredentials.setSignatureCount(record.getCounter());

        // Set the creation date, last used date, and last updated date
        passkeyCredentials.setLastUsedDate(Instant.now()); // If not available, this can be null

        // Set the type based on COSEKey's type
        passkeyCredentials.setType(convertCOSEKeyType(coseKey));

        // Set transports from the AuthenticatorTransport set
        passkeyCredentials.setTransports(convertTransports(record.getTransports()));

        // Set backup eligibility and state
        passkeyCredentials.setBackupEligible(Boolean.TRUE.equals(record.isBackupEligible()));
        passkeyCredentials.setBackupState(Boolean.TRUE.equals(record.isBackedUp()));

        return passkeyCredentials;
    }

    private static AttestationType convertAttestationStatementToType(AttestationStatement attestationStatement) {
        if (attestationStatement instanceof CertificateBaseAttestationStatement) {
            return AttestationType.DIRECT;
        }
        else if (attestationStatement instanceof NoneAttestationStatement) {
            return AttestationType.NONE;
        }
        else {
            throw new IllegalArgumentException("Unknown AttestationStatement type: " + attestationStatement.getClass().getName());
        }
    }

    private static String convertCOSEKeyType(COSEKey coseKey) {
        // Determine the key type based on the COSEKey instance
        return switch (coseKey) {
            case EC2COSEKey ignored -> "EC2";
            case EdDSACOSEKey ignored -> "EdDSA";
            case RSACOSEKey ignored -> "RSA";
            case null, default -> throw new IllegalArgumentException("Unknown COSEKey type: " + coseKey.getClass().getName());
        };
    }

    private static Transports convertTransports(Set<AuthenticatorTransport> transports) {
        // Example logic to convert AuthenticatorTransport set to your Transports enum
        if (transports.contains(AuthenticatorTransport.USB)) {
            return Transports.USB;
        }
        else if (transports.contains(AuthenticatorTransport.NFC)) {
            return Transports.NFC;
        }
        else if (transports.contains(AuthenticatorTransport.BLE)) {
            return Transports.BLE;
        }
        else if (transports.contains(AuthenticatorTransport.INTERNAL)) {
            return Transports.INTERNAL;
        }
        else if (transports.contains(AuthenticatorTransport.HYBRID)) {
            return Transports.HYBRID;
        }
        else {
            throw new IllegalArgumentException("Unknown AuthenticatorTransport: " + transports);
        }
    }
}

enum AttestationType {
    DIRECT, INDIRECT, NONE
}

enum Transports {
    USB, NFC, BLE, INTERNAL, HYBRID
}
