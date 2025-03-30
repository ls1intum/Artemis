package de.tum.cit.aet.artemis.core.domain;

import java.util.Set;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converts;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.springframework.security.core.userdetails.UserDetails;

import com.webauthn4j.data.AuthenticatorTransport;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.statement.AttestationStatement;
import com.webauthn4j.data.client.CollectedClientData;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import com.webauthn4j.data.extension.client.AuthenticationExtensionsClientOutputs;
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput;
import com.webauthn4j.springframework.security.credential.WebAuthnCredentialRecord;

import de.tum.cit.aet.artemis.core.domain.converter.AAGUIDConverter;
import de.tum.cit.aet.artemis.core.domain.converter.AttestationStatementConverter;
import de.tum.cit.aet.artemis.core.domain.converter.AuthenticatorExtensionsConverter;
import de.tum.cit.aet.artemis.core.domain.converter.COSEKeyConverter;
import de.tum.cit.aet.artemis.core.domain.converter.ClientExtensionsConverter;
import de.tum.cit.aet.artemis.core.domain.converter.CustomAuthenticatorTransportConverter;
import de.tum.cit.aet.artemis.core.domain.converter.CustomCollectedClientDataConverter;

@Entity
@Table(name = "passkey_credentials")
public class PasskeyCredentials extends AbstractAuditingEntity implements WebAuthnCredentialRecord {
    // TODO add implements WebAuthnCredentialRecord ?

    // TODO find better solution for converters? -> maybe a general wrapper possible?

    private String name;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private long counter;

    private boolean uvInitialized;

    private boolean backupEligible;

    private boolean backedUp;

    // TODO fix the database schema
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "m_transport", joinColumns = @JoinColumn(name = "credential_record_id"))
    @Column(name = "transport")
    @Convert(converter = CustomAuthenticatorTransportConverter.class)
    private Set<AuthenticatorTransport> transports;

    @Embedded
    @AttributeOverrides({ @AttributeOverride(name = "aaguid", column = @Column(name = "aaguid", columnDefinition = "binary")),
            @AttributeOverride(name = "credentialId", column = @Column(name = "credential_id", columnDefinition = "binary")),
            @AttributeOverride(name = "coseKey", column = @Column(name = "cose_key", columnDefinition = "binary")) })
    @Converts({ @Convert(converter = AAGUIDConverter.class, attributeName = "aaguid"), @Convert(converter = COSEKeyConverter.class, attributeName = "coseKey") })
    private AttestedCredentialData attestedCredentialData;

    // FIXME get rid of warnings
    // 2025-03-30T16:15:16.693+02:00 WARN 21511 --- [Artemis] [ main] o.h.metamodel.internal.MetadataContext : HHH015011: Unable to locate static metamodel field :
    // de.tum.cit.aet.artemis.core.domain.PasskeyCredentials_#attestationStatement; this may or may not indicate a problem with the static metamodel
    // 2025-03-30T16:15:16.694+02:00 WARN 21511 --- [Artemis] [ main] o.h.metamodel.internal.MetadataContext : HHH015011: Unable to locate static metamodel field :
    // de.tum.cit.aet.artemis.core.domain.PasskeyCredentials_#authenticatorExtensions; this may or may not indicate a problem with the static metamodel
    // 2025-03-30T16:15:16.694+02:00 WARN 21511 --- [Artemis] [ main] o.h.metamodel.internal.MetadataContext : HHH015011: Unable to locate static metamodel field :
    // de.tum.cit.aet.artemis.core.domain.PasskeyCredentials_#clientData; this may or may not indicate a problem with the static metamodel
    // 2025-03-30T16:15:16.694+02:00 WARN 21511 --- [Artemis] [ main] o.h.metamodel.internal.MetadataContext : HHH015011: Unable to locate static metamodel field :
    // de.tum.cit.aet.artemis.core.domain.PasskeyCredentials_#clientExtensions; this may or may not indicate a problem with the static metamodel

    @Lob
    @Convert(converter = AttestationStatementConverter.class)
    private AttestationStatement attestationStatement;

    // TODO is there a better solution than the "custom converter"?
    @Lob
    @Convert(converter = CustomCollectedClientDataConverter.class)
    private CollectedClientData clientData;

    @Lob
    @Convert(converter = ClientExtensionsConverter.class)
    private AuthenticationExtensionsClientOutputs<RegistrationExtensionClientOutput> clientExtensions;

    @Lob
    @Convert(converter = AuthenticatorExtensionsConverter.class)
    private AuthenticationExtensionsAuthenticatorOutputs<RegistrationExtensionAuthenticatorOutput> authenticatorExtensions;

    public String getFormat() {
        return attestationStatement.getFormat();
    }

    public String getName() {
        return name;
    }

    @Override
    public UserDetails getUserPrincipal() {
        return User.toUserDetails(user);
    }

    public void setName(String name) {
        this.name = name;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public long getCounter() {
        return counter;
    }

    public void setCounter(long counter) {
        this.counter = counter;
    }

    @Override
    public Set<AuthenticatorTransport> getTransports() {
        return transports;
    }

    public void setTransports(Set<AuthenticatorTransport> transports) {
        this.transports = transports;
    }

    @Override
    public AttestedCredentialData getAttestedCredentialData() {
        return attestedCredentialData;
    }

    public void setAttestedCredentialData(AttestedCredentialData attestedCredentialData) {
        this.attestedCredentialData = attestedCredentialData;
    }

    public AttestationStatement getAttestationStatement() {
        return attestationStatement;
    }

    public void setAttestationStatement(AttestationStatement attestationStatement) {
        this.attestationStatement = attestationStatement;
    }

    @Override
    public AuthenticationExtensionsClientOutputs<RegistrationExtensionClientOutput> getClientExtensions() {
        return clientExtensions;
    }

    public void setClientExtensions(AuthenticationExtensionsClientOutputs<RegistrationExtensionClientOutput> clientExtensions) {
        this.clientExtensions = clientExtensions;
    }

    @Override
    public AuthenticationExtensionsAuthenticatorOutputs<RegistrationExtensionAuthenticatorOutput> getAuthenticatorExtensions() {
        return authenticatorExtensions;
    }

    public void setAuthenticatorExtensions(AuthenticationExtensionsAuthenticatorOutputs<RegistrationExtensionAuthenticatorOutput> authenticatorExtensions) {
        this.authenticatorExtensions = authenticatorExtensions;
    }

    @Override
    public CollectedClientData getClientData() {
        return this.clientData;
    }

    public void setClientData(CollectedClientData clientData) {
        this.clientData = clientData;
    }

    @Override
    public Boolean isUvInitialized() {
        return this.uvInitialized;
    }

    @Override
    public void setUvInitialized(boolean value) {
        this.uvInitialized = value;
    }

    @Override
    public Boolean isBackupEligible() {
        return this.backupEligible;
    }

    @Override
    public void setBackupEligible(boolean value) {
        this.backupEligible = value;
    }

    @Override
    public Boolean isBackedUp() {
        return this.backedUp;
    }

    @Override
    public void setBackedUp(boolean value) {
        this.backedUp = value;
    }
}
